package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;

import edu.bits.itsm.assignment.AssignmentService;
import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.identity.Role;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.notification.NotificationService;
import edu.bits.itsm.shared.BusinessException;
import edu.bits.itsm.sla.SlaMonitor;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Uses real committed transactions, including the concurrent assignment and monitor checks. */
@SpringBootTest(properties = "app.sla.monitor-enabled=false")
class AutomationIntegrationTest {
  static final EmbeddedPostgres POSTGRES = start();
  static EmbeddedPostgres start() {
    try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").setPort(0).start(); }
    catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }
  @AfterAll static void close() throws Exception { POSTGRES.close(); }
  @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }
  @TestConfiguration static class TimeConfiguration {
    @Bean @Primary ControlledClock automationClock() { return new ControlledClock(); }
  }
  static class ControlledClock extends Clock {
    volatile Instant now = Instant.parse("2026-11-17T00:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
    void advance(long seconds) { now = now.plusSeconds(seconds); }
  }
  @Autowired JdbcTemplate jdbc;
  @Autowired ControlledClock clock;
  @Autowired UserAccountRepository users;
  @Autowired AccountService accounts;
  @Autowired PasswordEncoder encoder;
  @Autowired AssignmentService assignment;
  @Autowired IncidentService incidents;
  @Autowired CatalogService catalog;
  @Autowired SlaMonitor monitor;
  @Autowired NotificationService notifications;
  AccountPrincipal admin, employee, technician, other;
  long service, category;

  @BeforeEach void fixture() {
    clock.now = Instant.parse("2026-11-17T00:00:00Z");
    jdbc.execute("TRUNCATE user_accounts RESTART IDENTITY CASCADE");
    jdbc.update("DELETE FROM sla_policies WHERE service_id IS NOT NULL");
    jdbc.update("UPDATE sla_policies SET response_seconds=300,resolution_seconds=1800 WHERE priority='P1'");
    jdbc.update("INSERT INTO sla_policies(priority,response_seconds,resolution_seconds) SELECT 'P1',300,1800"
        + " WHERE NOT EXISTS(SELECT 1 FROM sla_policies WHERE priority='P1' AND service_id IS NULL)");
    jdbc.update("UPDATE assignment_configuration SET strategy='SKILL' WHERE id=1");
    admin = new AccountPrincipal(users.saveAndFlush(new UserAccount("Automation admin", "admin@automation.test",
        encoder.encode("Test-password-123"), Role.ADMIN)));
    employee = new AccountPrincipal(accounts.register("Reporter", "reporter@automation.test", "Test-password-123"));
    other = new AccountPrincipal(accounts.register("Other reporter", "other@automation.test", "Test-password-123"));
    technician = tech("first", 3);
    category = jdbc.queryForObject("SELECT id FROM categories WHERE name='Network'", Long.class);
    service = jdbc.queryForObject("SELECT id FROM it_services WHERE name='Campus Wi-Fi'", Long.class);
    assignment.skill(technician.getAccountId(), category, 3, admin.getAccountId());
  }
  AccountPrincipal tech(String suffix, int proficiency) {
    var t = new AccountPrincipal(accounts.createByAdmin("Technician " + suffix, suffix + "@automation.test",
        "Test-password-123", Role.TECHNICIAN, admin.getAccountId()));
    long network = jdbc.queryForObject("SELECT id FROM categories WHERE name='Network'", Long.class);
    assignment.skill(t.getAccountId(), network, proficiency, admin.getAccountId());
    return t;
  }
  Incident create() { return incidents.create(employee, "Automation Wi-Fi incident", "Test-owned data",
      service, category, 3, 3, 10); }
  Incident current(long id) { return incidents.view(id, admin); }
  void action(long id, String action, AccountPrincipal actor) {
    incidents.transition(id, current(id).getVersion(), action, "Acceptance details", actor);
  }
  void resolve(long id, AccountPrincipal actor) {
    action(id, "ACKNOWLEDGE", actor); action(id, "START", actor); action(id, "RESOLVE", actor);
  }
  long count(String table, long id) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE incident_id=?", Long.class, id);
  }
  List<String> events(long id) {
    return jdbc.queryForList("SELECT kind FROM sla_events WHERE incident_id=? ORDER BY kind", String.class, id);
  }
  <T> List<T> together(Callable<T> operation) throws Exception {
    var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    Callable<T> task = () -> { ready.countDown(); assertTrue(go.await(5, TimeUnit.SECONDS)); return operation.call(); };
    try {
      var a = pool.submit(task); var b = pool.submit(task);
      assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
      return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    } finally { go.countDown(); pool.shutdownNow(); }
  }

  @ParameterizedTest @ValueSource(strings = {"SKILL", "LOAD"})
  void concurrentReportsCannotExceedLastAvailableSlot(String strategy) throws Exception {
    assignment.configure(strategy, admin.getAccountId());
    assignment.technician(technician.getAccountId(), true, 1, admin.getAccountId());
    var ids = together(() -> create().getId());
    assertEquals(1, ids.stream().filter(id -> current(id).getAssigneeId() != null).count());
    assertEquals(1, ids.stream().filter(id -> current(id).getStatus() == IncidentStatus.CLASSIFIED).count());
    assertEquals(0, assignment.candidates(category).size());
  }

  @Test void bothStrategiesUsePersistedWorkloadProficiencyAndStableTies() {
    long first = create().getId();
    var second = tech("second", 2);
    var tie = tech("third", 2);
    assertEquals(technician.getAccountId(), create().getAssigneeId());
    assignment.configure("LOAD", admin.getAccountId());
    assertEquals(second.getAccountId(), create().getAssigneeId());
    assertEquals(tie.getAccountId(), create().getAssigneeId());
    assertEquals(technician.getAccountId(), current(first).getAssigneeId());
  }

  @ParameterizedTest @ValueSource(strings = {"unavailable", "inactive", "missing-skill", "full"})
  void ineligibleTechnicianLeavesTrackedClassifiedIncident(String exclusion) {
    switch (exclusion) {
      case "unavailable" -> assignment.technician(technician.getAccountId(), false, 5, admin.getAccountId());
      case "inactive" -> accounts.setActive(technician.getAccountId(), false, admin.getAccountId());
      case "missing-skill" -> assignment.skill(technician.getAccountId(), category, 0, admin.getAccountId());
      case "full" -> { assignment.technician(technician.getAccountId(), true, 1, admin.getAccountId()); create(); }
    }
    var i = create();
    assertEquals(IncidentStatus.CLASSIFIED, i.getStatus()); assertNull(i.getAssigneeId());
    assertEquals(i.getCreatedAt().plusSeconds(300), i.getResponseDue());
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE incident_id=? AND user_id=? AND subject='Assignment required'",
        Integer.class, i.getId(), admin.getAccountId()));
  }

  @ParameterizedTest @ValueSource(strings = {"RESOLVED", "VERIFIED", "CLOSED"})
  void completedWorkDoesNotConsumeCapacity(String state) {
    assignment.technician(technician.getAccountId(), true, 1, admin.getAccountId());
    var i = create(); resolve(i.getId(), technician);
    if (state.equals("VERIFIED")) action(i.getId(), "VERIFY", employee);
    if (state.equals("CLOSED")) action(i.getId(), "VERIFY_CLOSE", employee);
    assertEquals(technician.getAccountId(), create().getAssigneeId());
  }

  @Test void reassignmentRequiresNewAcknowledgmentAndRetainsFirstResponseAndDeadlines() {
    var i = create(); clock.advance(50); action(i.getId(), "ACKNOWLEDGE", technician);
    var acknowledged = current(i.getId()).getAcknowledgedAt();
    var second = tech("second", 2); clock.advance(100);
    incidents.assign(i.getId(), current(i.getId()).getVersion(), second.getAccountId(), "Shift change", admin);
    var assigned = current(i.getId());
    assertEquals(IncidentStatus.ASSIGNED, assigned.getStatus());
    assertEquals(i.getResponseDue(), assigned.getResponseDue()); assertEquals(i.getResolutionDue(), assigned.getResolutionDue());
    assertThrows(BusinessException.class, () -> action(i.getId(), "START", second));
    action(i.getId(), "ACKNOWLEDGE", second);
    assertEquals(acknowledged, current(i.getId()).getAcknowledgedAt());
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM audit_entries WHERE incident_id=? AND action='ACKNOWLEDGED'", Integer.class, i.getId()));
  }

  @Test void manualAssignmentRejectsInvalidEligibilityAndReasonWithoutChangingExistingWork() {
    var i = create(); var second = tech("second", 2);
    assignment.technician(second.getAccountId(), false, 5, admin.getAccountId());
    long audit = count("audit_entries", i.getId()), notices = count("notifications", i.getId());
    assertThrows(BusinessException.class, () -> incidents.assign(i.getId(), i.getVersion(), second.getAccountId(), "Shift change", admin));
    assertThrows(BusinessException.class, () -> incidents.assign(i.getId(), i.getVersion(), technician.getAccountId(), " ", admin));
    assertEquals(technician.getAccountId(), current(i.getId()).getAssigneeId());
    assertEquals(i.getVersion(), current(i.getId()).getVersion());
    assertEquals(audit, count("audit_entries", i.getId())); assertEquals(notices, count("notifications", i.getId()));
  }

  @Test void pendingAssignmentCanBeRetriedAfterAvailabilityReturns() {
    assignment.technician(technician.getAccountId(), false, 5, admin.getAccountId());
    var i = create(); clock.advance(100);
    assignment.technician(technician.getAccountId(), true, 5, admin.getAccountId());
    incidents.assign(i.getId(), i.getVersion(), null, "Technician returned", admin);
    assertEquals(technician.getAccountId(), current(i.getId()).getAssigneeId());
    assertEquals(i.getResponseDue(), current(i.getId()).getResponseDue());
  }

  @Test void servicePolicyOverridesDefaultAndLaterPolicyEditsOnlyAffectNewTickets() {
    var normal = create();
    catalog.savePolicy(service, "P1", 10, 30, admin.getAccountId());
    var overridden = create();
    catalog.savePolicy(service, "P1", 20, 60, admin.getAccountId());
    assertEquals(300, current(normal.getId()).getResponseSeconds());
    assertEquals(10, current(overridden.getId()).getResponseSeconds());
    assertEquals(20, create().getResponseSeconds());
    incidents.correct(overridden.getId(), overridden.getVersion(), category, 3, 3, "Category reviewed", admin);
    assertEquals(overridden.getResponseDue(), current(overridden.getId()).getResponseDue());
    assertEquals(overridden.getResolutionDue(), current(overridden.getId()).getResolutionDue());
  }

  @Test void absentPolicyRejectsCreationWithoutOrphanWrites() {
    jdbc.update("DELETE FROM sla_policies WHERE priority='P1'");
    assertThrows(BusinessException.class, this::create);
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM incidents", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM resolution_cycles", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
  }

  @Test void reprioritizationKeepsElapsedAnchorAndExistingBreach() {
    var i = create(); clock.advance(301); monitor.scan();
    incidents.correct(i.getId(), i.getVersion(), category, 2, 2, "Impact verified", admin);
    var revised = current(i.getId());
    assertEquals(i.getCreatedAt().plusSeconds(900), revised.getResponseDue());
    assertEquals(i.getCycleStartedAt().plusSeconds(7200), revised.getResolutionDue());
    assertEquals(List.of("RESPONSE_BREACH"), events(i.getId()));
  }

  @Test void warningAndBreachAreOncePerTargetWithRelevantRecipientsAndBothChannels() {
    var i = create(); clock.advance(240); monitor.scan(); monitor.scan();
    assertEquals(List.of("RESPONSE_WARNING"), events(i.getId()));
    clock.advance(1200); monitor.scan(); monitor.scan();
    assertEquals(List.of("RESOLUTION_WARNING", "RESPONSE_BREACH", "RESPONSE_WARNING"), events(i.getId()));
    clock.advance(361); monitor.scan(); monitor.scan();
    assertEquals(4, events(i.getId()).size());
    for (String subject : List.of("RESPONSE WARNING", "RESPONSE BREACH", "RESOLUTION WARNING", "RESOLUTION BREACH")) {
      var rows = jdbc.queryForList("SELECT user_id,channel,delivery_status FROM notifications WHERE incident_id=? AND subject=?", i.getId(), subject);
      assertEquals(6, rows.size());
      assertEquals(Set.of(admin.getAccountId(), employee.getAccountId(), technician.getAccountId()),
          rows.stream().map(r -> ((Number) r.get("user_id")).longValue()).collect(java.util.stream.Collectors.toSet()));
      for (var row : rows) assertEquals(row.get("channel").equals("IN_APP") ? "DELIVERED" : "SIMULATED", row.get("delivery_status"));
    }
    assertTrue(notifications.inbox(other.getAccountId()).isEmpty());
  }

  @Test void firstOverdueScanRecordsBreachesWithoutBackfilledWarnings() {
    var i = create(); clock.advance(1801); monitor.scan();
    assertEquals(List.of("RESOLUTION_BREACH", "RESPONSE_BREACH"), events(i.getId()));
  }

  @Test void concurrentMonitorsPersistOneEventAndOneDeliveryPerRecipientChannel() throws Exception {
    var i = create(); clock.advance(240);
    together(() -> { monitor.scan(); return true; });
    assertEquals(List.of("RESPONSE_WARNING"), events(i.getId()));
    assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE incident_id=? AND subject='RESPONSE WARNING'", Integer.class, i.getId()));
  }

  @Test void simulatedEmailFailureRollsBackCreationAndBothChannels() {
    long audits = jdbc.queryForObject("SELECT count(*) FROM audit_entries", Long.class);
    jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT test_delivery_failure CHECK(channel <> 'SIMULATED_EMAIL') NOT VALID");
    try { assertThrows(DataAccessException.class, this::create); }
    finally { jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT test_delivery_failure"); }
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM incidents", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
    assertEquals(audits, jdbc.queryForObject("SELECT count(*) FROM audit_entries", Long.class));
    assertNotNull(create().getId());
  }

  @Test void failedSlaDeliveryRollsBackEventAndRetriesWithoutLossOrDuplicateMessages() {
    var i = create(); clock.advance(240);
    long audits = count("audit_entries", i.getId()), notices = count("notifications", i.getId());
    jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT test_delivery_failure CHECK(subject <> 'RESPONSE WARNING' OR channel <> 'SIMULATED_EMAIL') NOT VALID");
    try { assertThrows(DataAccessException.class, monitor::scan); }
    finally { jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT test_delivery_failure"); }
    assertTrue(events(i.getId()).isEmpty()); assertEquals(audits, count("audit_entries", i.getId()));
    assertEquals(notices, count("notifications", i.getId()));
    monitor.scan(); monitor.scan();
    assertEquals(1, events(i.getId()).size()); assertEquals(notices + 6, count("notifications", i.getId()));
  }

  @Test void readingNotificationCannotMarkSomeoneElsesInboxOrEmailAsRead() {
    var i = create();
    long id = jdbc.queryForObject("SELECT min(id) FROM notifications WHERE incident_id=? AND user_id=? AND channel='IN_APP'", Long.class, i.getId(), employee.getAccountId());
    notifications.read(id, other.getAccountId());
    assertNull(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", java.sql.Timestamp.class, id));
    notifications.read(id, employee.getAccountId());
    assertNotNull(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?", java.sql.Timestamp.class, id));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE channel='SIMULATED_EMAIL' AND read_at IS NOT NULL", Integer.class));
  }

  @Test void skillConfigurationRejectsNonTechniciansWithReadableError() {
    assertThrows(BusinessException.class, () -> assignment.skill(employee.getAccountId(), category, 2, admin.getAccountId()));
    assertThrows(BusinessException.class, () -> assignment.skill(Long.MAX_VALUE, category, 2, admin.getAccountId()));
  }
}
