package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;

import edu.bits.itsm.ai.AiSuggestionStore;
import edu.bits.itsm.assignment.AssignmentService;
import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.identity.Role;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.shared.ConflictException;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;

/** No test transaction: each application service call must commit or roll back for real. */
@SpringBootTest(properties = "app.sla.monitor-enabled=false")
class AuditConsistencyIntegrationTest {
  static final EmbeddedPostgres POSTGRES = start();
  static EmbeddedPostgres start() {
    try { return EmbeddedPostgres.builder().setPort(0).start(); }
    catch (Exception e) { throw new ExceptionInInitializerError(e); }
  }
  @AfterAll static void close() throws Exception { POSTGRES.close(); }
  @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }
  @TestConfiguration static class TimeConfiguration {
    @Bean @Primary ControlledClock auditTestClock() { return new ControlledClock(); }
  }
  static class ControlledClock extends Clock {
    Instant now = Instant.parse("2026-11-16T00:00:00Z");
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
  @Autowired IncidentHistory history;
  @Autowired AuditService audit;
  @Autowired AiSuggestionStore suggestions;
  AccountPrincipal admin, employee, technician, other;
  long service, category;

  @BeforeEach void fixture() {
    clock.now = Instant.parse("2026-11-16T00:00:00Z");
    // This is an isolated test-owned database, never the persistent development database.
    jdbc.execute("TRUNCATE user_accounts RESTART IDENTITY CASCADE");
    admin = new AccountPrincipal(users.saveAndFlush(new UserAccount("Audit admin", "admin@audit.test",
        encoder.encode("Test-password-123"), Role.ADMIN)));
    employee = new AccountPrincipal(accounts.register("Audit reporter", "reporter@audit.test", "Test-password-123"));
    other = new AccountPrincipal(accounts.register("Other reporter", "other@audit.test", "Test-password-123"));
    technician = new AccountPrincipal(accounts.createByAdmin("Audit technician", "tech@audit.test",
        "Test-password-123", Role.TECHNICIAN, admin.getAccountId()));
    category = jdbc.queryForObject("SELECT id FROM categories WHERE name='Network'", Long.class);
    service = jdbc.queryForObject("SELECT id FROM it_services WHERE name='Campus Wi-Fi'", Long.class);
    assignment.skill(technician.getAccountId(), category, 3, admin.getAccountId());
  }
  Incident create() {
    return incidents.create(employee, "Audit consistency ticket", "Test-owned data", service, category, 3, 3, 10);
  }
  Incident current(long id) { return incidents.view(id, admin); }
  void action(long id, String action, String details, AccountPrincipal actor) {
    incidents.transition(id, current(id).getVersion(), action, details, actor);
  }
  void investigate(long id) {
    action(id, "ACKNOWLEDGE", "", technician);
    action(id, "START", "", technician);
  }
  void resolve(long id) {
    investigate(id);
    action(id, "RESOLVE", "Documented solution", technician);
  }
  long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }
  long count(String table, long incident) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE incident_id=?", Long.class, incident);
  }
  record Snapshot(long version, IncidentStatus status, Long assignee, int cycle,
      long revision, Instant responseDue, Instant resolutionDue,
      long messages, long resolutions, long cycles, long feedback, long audits, long notifications) {}
  Snapshot snapshot(long id) {
    var i = current(id);
    return new Snapshot(i.getVersion(), i.getStatus(), i.getAssigneeId(), i.getCycle(),
        i.getConversationRevision(), i.getResponseDue(), i.getResolutionDue(),
        count("incident_messages",id), count("resolutions",id), count("resolution_cycles",id),
        count("feedback",id), count("audit_entries",id), count("notifications",id));
  }
  void forceFailure(String table, String expression, Runnable operation) {
    // NOT VALID skips historical rows but rejects matching new writes. Always remove the test constraint.
    jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT test_forced_failure CHECK (" + expression + ") NOT VALID");
    try { assertThrows(DataAccessException.class, operation::run); }
    finally { jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT test_forced_failure"); }
  }

  @Test void auditCannotWriteOutsideBusinessTransaction() {
    long before=count("audit_entries");
    assertThrows(IllegalTransactionStateException.class,
        () -> audit.record(admin.getAccountId(), "ORPHAN", "TEST", "Not committed"));
    assertThrows(IllegalTransactionStateException.class,
        () -> audit.incident(admin.getAccountId(), 1, "ORPHAN", null, null, "Not committed"));
    assertEquals(before,count("audit_entries"));
  }

  @Test void failedCreationRollsBackInsertedIncidentAndInitialHistory() {
    long audits=count("audit_entries");
    forceFailure("audit_entries", "action <> 'CLASSIFIED'", () -> create());
    assertEquals(0,count("incidents"));assertEquals(0,count("resolution_cycles"));
    assertEquals(0,count("notifications"));assertEquals(audits,count("audit_entries"));
    assertNotNull(create().getId()); // Sequence gaps after a rollback are valid.
  }

  @Test void failedMessageAuditRollsBackMessageAndRevision() {
    var i=create();var before=snapshot(i.getId());
    forceFailure("audit_entries", "action <> 'MESSAGE_ADDED'", () ->
        incidents.message(i.getId(),i.getVersion(),"PUBLIC","Must roll back",null,employee));
    assertEquals(before,snapshot(i.getId()));
  }

  @Test void failedResolutionInsertRollsBackStateAndResolutionAudit() {
    var i=create();investigate(i.getId());var before=snapshot(i.getId());
    forceFailure("resolutions", "details <> 'Force failing resolution'", () ->
        action(i.getId(),"RESOLVE","Force failing resolution",technician));
    assertEquals(before,snapshot(i.getId()));
    assertNull(current(i.getId()).getResolvedAt());
  }

  @Test void lateClosureFlushFailureRollsBackBothAuditEventsAndNotifications() {
    var i=create();resolve(i.getId());var before=snapshot(i.getId());
    forceFailure("incidents", "status <> 'CLOSED'", () ->
        action(i.getId(),"VERIFY_CLOSE","",employee));
    assertEquals(before,snapshot(i.getId()));
    assertNull(current(i.getId()).getClosedAt());
    assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_entries WHERE incident_id=? AND action IN ('VERIFIED','CLOSED')",Integer.class,i.getId()));
  }

  @Test void failedReassignmentAfterRejectionRollsBackNewCycleAndKeepsOldResolution() {
    var i=create();resolve(i.getId());var before=snapshot(i.getId());
    var resolved=current(i.getId()).getResolvedAt();
    forceFailure("audit_entries", "action <> 'ASSIGNED'", () ->
        action(i.getId(),"REJECT","Fix did not work",employee));
    assertEquals(before,snapshot(i.getId()));
    assertEquals(resolved,current(i.getId()).getResolvedAt());
    assertEquals(resolved,jdbc.queryForObject("SELECT resolved_at FROM resolution_cycles WHERE incident_id=? AND cycle=1",java.sql.Timestamp.class,i.getId()).toInstant());
  }

  @Test void failedFeedbackAuditRollsBackFeedbackRow() {
    var i=create();resolve(i.getId());var before=snapshot(i.getId());
    forceFailure("audit_entries", "action <> 'FEEDBACK_ADDED'", () ->
        incidents.feedback(i.getId(),5,"Must not persist",employee));
    assertEquals(before,snapshot(i.getId()));
    incidents.feedback(i.getId(),5,"Now committed",employee);
    assertEquals(1,count("feedback",i.getId()));
  }

  @Test void everyHistoryReadEnforcesOwnershipAndStaffPrivileges() {
    var i=create();long id=i.getId();
    assertThrows(AccessDeniedException.class,()->history.messages(id,other));
    assertThrows(AccessDeniedException.class,()->history.audit(id,other));
    assertThrows(AccessDeniedException.class,()->history.resolutions(id,other));
    assertThrows(AccessDeniedException.class,()->history.events(id,other));
    assertThrows(AccessDeniedException.class,()->history.feedback(id,other));
    assertThrows(AccessDeniedException.class,()->history.suggestions(id,employee));
    assertThrows(AccessDeniedException.class,()->history.allAudit(employee));
    assertThrows(AccessDeniedException.class,()->history.email(technician));
    assertFalse(history.allAudit(admin).isEmpty());
    jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",admin.getAccountId());
    assertThrows(AccessDeniedException.class,()->history.allAudit(admin));
    assertThrows(AccessDeniedException.class,()->history.email(admin));
    assertThrows(AccessDeniedException.class,()->history.audit(id,admin));
  }

  @Test void orderedHistoryReconstructsLifecycleWithoutLeakingPrivateEntries() {
    var i=create();long id=i.getId();
    incidents.message(id,current(id).getVersion(),"INTERNAL","Private diagnostic body",null,technician);
    incidents.message(id,current(id).getVersion(),"REQUEST","Which room?",null,technician);
    long request=jdbc.queryForObject("SELECT id FROM incident_messages WHERE incident_id=? AND kind='REQUEST'",Long.class,id);
    incidents.message(id,current(id).getVersion(),"PUBLIC","Library",request,employee);
    suggestions.save(id,current(id).getVersion(),technician,"RESOLUTION",null,
        "Explicit test fixture; no provider call", "test-fixture-model");
    incidents.assign(id,current(id).getVersion(),technician.getAccountId(),"Administrative reassignment check",admin);
    incidents.correct(id,current(id).getVersion(),category,2,2,"Verified impact correction",admin);
    clock.advance(7201); // Include durable system breach events in the reconstructed chain.
    resolve(id);action(id,"REJECT","Still broken",employee);resolve(id);
    action(id,"VERIFY_CLOSE","",employee);incidents.feedback(id,5,"Resolved",employee);
    var rows=history.audit(id,admin);
    IncidentStatus state=null;long previousId=-1;
    for(var row:rows) {
      long eventId=((Number)row.get("id")).longValue();assertTrue(eventId>previousId);previousId=eventId;
      assertEquals(id,((Number)row.get("incident_id")).longValue());
      assertEquals("INC-"+id,row.get("subject"));
      assertNotNull(row.get("created_at"));assertNotNull(row.get("actor_name"));
      assertNotNull(row.get("description"));
      String previous=(String)row.get("previous_state");String next=(String)row.get("new_state");
      assertEquals(state==null?null:state.name(),previous,"History must chain at "+row.get("action"));
      assertNotNull(next);var nextState=IncidentStatus.valueOf(next);
      if(state!=null && state!=nextState) IncidentState.forStatus(state).checkTransition(nextState);
      state=nextState;
    }
    assertEquals(IncidentStatus.CLOSED,state);assertEquals(state,current(id).getStatus());
    assertEquals(2,history.resolutions(id,employee).size());
    assertTrue(rows.stream().anyMatch(r->r.get("action").equals("REASSIGNMENT")));
    assertTrue(rows.stream().anyMatch(r->r.get("actor_id")==null && r.get("actor_name").equals("System")));
    var publicRows=history.audit(id,employee);
    assertEquals(rows.size()-2,publicRows.size());
    assertFalse(publicRows.stream().anyMatch(r->r.get("action").equals("AI_SUGGESTION_GENERATED")
        ||r.get("description").toString().startsWith("INTERNAL message")));
    assertTrue(history.messages(id,employee).stream().noneMatch(r->r.get("body").equals("Private diagnostic body")));
  }

  @Test void concurrentSameVersionMessagesCommitOneChangeAndOneAudit() throws Exception {
    var i=create();long version=i.getVersion();var before=snapshot(i.getId());
    var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
    var pool=Executors.newFixedThreadPool(2);
    try {
      Callable<String> post=()-> {
        ready.countDown();assertTrue(go.await(5,TimeUnit.SECONDS));
        try { incidents.message(i.getId(),version,"PUBLIC","Concurrent draft",null,employee);return "SAVED"; }
        catch(ConflictException e) { return "CONFLICT"; }
      };
      var first=pool.submit(post);var second=pool.submit(post);
      assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
      assertEquals(Set.of("SAVED","CONFLICT"),Set.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)));
      var after=snapshot(i.getId());
      assertEquals(before.messages()+1,after.messages());assertEquals(before.audits()+1,after.audits());
      assertEquals(before.revision()+1,after.revision());assertEquals(before.version()+1,after.version());
      assertEquals(before.notifications()+6,after.notifications());
    } finally { go.countDown();pool.shutdownNow(); }
  }
}
