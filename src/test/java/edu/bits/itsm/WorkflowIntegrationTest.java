package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.bits.itsm.assignment.AssignmentService;
import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.identity.Role;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.reporting.IncidentQueryService;
import edu.bits.itsm.shared.*;
import edu.bits.itsm.sla.SlaMonitor;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.sla.monitor-enabled=false")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class WorkflowIntegrationTest {
  static final EmbeddedPostgres POSTGRES = start();

  static EmbeddedPostgres start() {
    try {
      return EmbeddedPostgres.builder().setPort(0).start();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @AfterAll
  static void close() throws Exception {
    POSTGRES.close();
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }

  @TestConfiguration
  static class ClockConfiguration {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    Instant now = Instant.parse("2026-11-15T10:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }

    void advance(long seconds) {
      now = now.plusSeconds(seconds);
    }
  }

  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired AccountService accounts;
  @Autowired UserAccountRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired IncidentService incidents;
  @Autowired IncidentRepository repository;
  @Autowired AssignmentService assignment;
  @Autowired SlaMonitor monitor;
  @Autowired CatalogService catalog;
  @Autowired IncidentQueryService queries;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestClock clock;
  AccountPrincipal employee, other, technician, admin;
  long category, service;

  @BeforeEach
  void setup() {
    clock.now = Instant.parse("2026-11-15T10:00:00Z");
    var a =
        users.saveAndFlush(
            new UserAccount(
                "Admin",
                "admin@workflow.test",
                encoder.encode("Test-password-123"),
                edu.bits.itsm.identity.Role.ADMIN));
    admin = new AccountPrincipal(a);
    employee =
        new AccountPrincipal(
            accounts.register("Reporter", "reporter@workflow.test", "Test-password-123"));
    other =
        new AccountPrincipal(
            accounts.register("Other", "other@workflow.test", "Test-password-123"));
    technician =
        new AccountPrincipal(
            accounts.createByAdmin(
                "Technician",
                "tech@workflow.test",
                "Test-password-123",
                edu.bits.itsm.identity.Role.TECHNICIAN,
                a.getId()));
    category = jdbc.queryForObject("SELECT id FROM categories WHERE name='Network'", Long.class);
    service =
        jdbc.queryForObject("SELECT id FROM it_services WHERE name='Campus Wi-Fi'", Long.class);
    assignment.skill(technician.getAccountId(), category, 3, admin.getAccountId());
  }

  Incident create() {
    return incidents.create(
        employee, "Campus network down", "Unable to connect", service, category, 3, 3, 50);
  }

  void resolve(Incident i) {
    incidents.transition(i.getId(), i.getVersion(), "ACKNOWLEDGE", "", technician);
    incidents.transition(i.getId(), i.getVersion(), "START", "", technician);
    incidents.transition(
        i.getId(), i.getVersion(), "RESOLVE", "Restored access after troubleshooting.", technician);
  }

  @Test
  void completeWorkflowPersistsAuditNotificationsAndFeedback() {
    var i = create();
    assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
    assertEquals(technician.getAccountId(), i.getAssigneeId());
    assertEquals("P1", i.getAssessment().getPriority());
    resolve(i);
    incidents.transition(i.getId(), i.getVersion(), "VERIFY_CLOSE", "", employee);
    incidents.feedback(i.getId(), 5, "Fixed", employee);
    assertEquals(IncidentStatus.CLOSED, i.getStatus());
    assertTrue(
        jdbc.queryForObject(
                "SELECT count(*) FROM audit_entries WHERE incident_id=?", Long.class, i.getId())
            >= 8);
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(DISTINCT channel) FROM notifications WHERE incident_id=?",
            Integer.class,
            i.getId()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM feedback WHERE incident_id=?", Integer.class, i.getId()));
  }

  @Test
  void crossReporterAccessIsDenied() {
    var i = create();
    assertThrows(AccessDeniedException.class, () -> incidents.view(i.getId(), other));
    assertEquals(0, queries.count(other, new IncidentFilter()));
  }

  @Test
  void noSkillsLeavesClassifiedAndTracked() {
    assignment.skill(technician.getAccountId(), category, 0, admin.getAccountId());
    var i = create();
    assertEquals(IncidentStatus.CLASSIFIED, i.getStatus());
    assertNull(i.getAssigneeId());
    assertNotNull(i.getResolutionDue());
  }

  @Test
  void technicianCapacityIsRespected() {
    assignment.technician(technician.getAccountId(), true, 1, admin.getAccountId());
    assertEquals(IncidentStatus.ASSIGNED, create().getStatus());
    assertEquals(IncidentStatus.CLASSIFIED, create().getStatus());
  }

  @Test
  void strategiesChangeNewAssignmentsOnly() {
    var second =
        accounts.createByAdmin(
            "Second",
            "second@workflow.test",
            "Test-password-123",
            edu.bits.itsm.identity.Role.TECHNICIAN,
            admin.getAccountId());
    assignment.skill(second.getId(), category, 2, admin.getAccountId());
    var first = create();
    assignment.configure("LOAD", admin.getAccountId());
    assertEquals(second.getId(), create().getAssigneeId());
    assertEquals(technician.getAccountId(), first.getAssigneeId());
  }

  @Test
  void staleVersionIsRejected() {
    var i = create();
    long stale = i.getVersion();
    incidents.transition(i.getId(), stale, "ACKNOWLEDGE", "", technician);
    assertThrows(
        ConflictException.class,
        () -> incidents.message(i.getId(), stale, "PUBLIC", "Old edit", null, employee));
  }

  @Test
  void invalidLifecycleShortcutIsRejected() {
    var i = create();
    assertThrows(
        BusinessException.class,
        () -> incidents.transition(i.getId(), i.getVersion(), "CLOSE", "", employee));
  }

  @Test
  void slaWarningsAndBreachesAreDeduplicated() {
    var i = create();
    clock.advance(240);
    monitor.scan();
    monitor.scan();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM sla_events WHERE incident_id=? AND kind='RESPONSE_WARNING'",
            Integer.class,
            i.getId()));
    clock.advance(61);
    monitor.scan();
    monitor.scan();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM sla_events WHERE incident_id=? AND kind='RESPONSE_BREACH'",
            Integer.class,
            i.getId()));
  }

  @Test
  void exactDeadlineAcknowledgmentIsOnTime() {
    var i = create();
    clock.advance(300);
    incidents.transition(i.getId(), i.getVersion(), "ACKNOWLEDGE", "", technician);
    clock.advance(1);
    monitor.scan();
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM sla_events WHERE incident_id=? AND kind='RESPONSE_BREACH'",
            Integer.class,
            i.getId()));
  }

  @Test
  void policyEditsDoNotRewriteExistingDeadline() {
    var i = create();
    Instant due = i.getResolutionDue();
    catalog.savePolicy(service, "P1", 10, 20, admin.getAccountId());
    assertEquals(due, i.getResolutionDue());
    assertEquals(clock.instant().plusSeconds(20), create().getResolutionDue());
  }

  @Test
  void rejectedResolutionCreatesAnotherCycle() {
    var i = create();
    resolve(i);
    Instant first = i.getFirstResolvedAt();
    clock.advance(50);
    incidents.transition(i.getId(), i.getVersion(), "REJECT", "Not fixed", employee);
    assertEquals(2, i.getCycle());
    assertEquals(first, i.getFirstResolvedAt());
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(*) FROM resolution_cycles WHERE incident_id=?",
            Integer.class,
            i.getId()));
  }

  @Test
  void textAndCategoryFiltersUsePersistentData() {
    var i = create();
    var f = new IncidentFilter();
    f.setQuery("INC-" + i.getId());
    f.setCategory(category);
    assertEquals(1, queries.count(employee, f));
    f.setPriority("P4");
    assertEquals(0, queries.count(employee, f));
    assertEquals(1L, queries.metrics(employee, new IncidentFilter()).get("total"));
  }
  @Test
  void employeeReportsIncidentAndViewsOnlyOwnDetailsOverHttp() throws Exception {
    mvc.perform(get("/employee/incidents/new").with(user(employee))).andExpect(status().isOk());
    var result = mvc.perform(post("/employee/incidents").with(user(employee)).with(csrf())
        .param("title", "Wi-Fi report <script>unsafe</script>")
        .param("description", "Library Wi-Fi unavailable")
        .param("service", ""+service).param("category", ""+category)
        .param("impact","2").param("urgency","2").param("affected","20")
        .param("priority","P1").param("status","CLOSED").param("reporterId",""+other.getAccountId()))
        .andExpect(status().is3xxRedirection()).andReturn();
    String detail = result.getResponse().getRedirectedUrl();
    long id = Long.parseLong(detail.substring(detail.lastIndexOf('/')+1));
    var incident = incidents.view(id,employee);
    assertEquals(employee.getAccountId(),incident.getReporterId());
    assertEquals(IncidentStatus.ASSIGNED,incident.getStatus());
    assertEquals("P2",incident.getAssessment().getPriority());
    mvc.perform(get(detail).with(user(employee))).andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Score 12 = 2 × 2 × 3")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>unsafe</script>"))));
    mvc.perform(get(detail).with(user(other))).andExpect(status().isForbidden());
    mvc.perform(get("/api/incidents/"+id).with(user(other))).andExpect(status().isForbidden());
    mvc.perform(get("/incidents").with(user(other)))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Library Wi-Fi unavailable"))));
  }

  @Test
  void invalidReportRetainsInputAndCreatesNothing() throws Exception {
    long before = repository.count();
    mvc.perform(post("/employee/incidents").with(user(employee)).with(csrf())
        .param("title","Keep my title").param("description","Keep my description")
        .param("service",""+service).param("category",""+category)
        .param("impact","4").param("urgency","2").param("affected","not-a-number"))
        .andExpect(status().isBadRequest()).andExpect(view().name("incidents/new"))
        .andExpect(model().attributeHasFieldErrors("form","impact","affected"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Keep my description")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter a whole number of affected users")));
    assertEquals(before, repository.count());
  }

  @Test
  void inactiveCatalogAndCsrfRejectReportWithoutPersisting() throws Exception {
    long before = repository.count();
    jdbc.update("UPDATE it_services SET active=false WHERE id=?",service);
    mvc.perform(post("/employee/incidents").with(user(employee)).with(csrf())
        .param("title","Keep title").param("description","Keep description")
        .param("service",""+service).param("category",""+category)
        .param("impact","1").param("urgency","1").param("affected","1"))
        .andExpect(status().isBadRequest()).andExpect(model().attributeHasErrors("form"));
    mvc.perform(post("/employee/incidents").with(user(employee)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/employee/incidents").with(user(technician)).with(csrf()))
        .andExpect(status().isForbidden());
    assertEquals(before,repository.count());
  }

  @Test
  void correctionUsesSnapshotRequiresReasonAndAuditsInputs() throws Exception {
    var i = incidents.create(employee,"Priority snapshot","Details",service,category,1,1,1);
    assertEquals("P3",i.getAssessment().getPriority());
    jdbc.update("UPDATE it_services SET criticality=1 WHERE id=?",service);
    incidents.correct(i.getId(),i.getVersion(),category,3,3,"Campus-wide outage confirmed",admin);
    assertEquals(3,i.getAssessment().getCriticality());
    assertEquals("P1",i.getAssessment().getPriority());
    assertEquals(27,i.getAssessment().getScore());
    String description = jdbc.queryForObject(
        "SELECT description FROM audit_entries WHERE incident_id=? AND action='INCIDENT_CORRECTED'",
        String.class,i.getId());
    assertTrue(description.contains("impact=1 -> 3"));
    assertTrue(description.contains("urgency=1 -> 3"));
    mvc.perform(post("/admin/incidents/"+i.getId()+"/correction").with(user(admin)).with(csrf())
        .param("version",""+i.getVersion()).param("category",""+category)
        .param("impact","2").param("urgency","2").param("reason"," "))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/admin/incidents/"+i.getId()+"/correction").with(user(employee)).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void lifecycleHttpRejectsWrongRoleShortcutAndStaleEdit() throws Exception {
    var i = create();
    String action = "/incidents/"+i.getId()+"/action";
    mvc.perform(post(action).with(user(employee)).with(csrf()).param("version",""+i.getVersion())
        .param("action","ACKNOWLEDGE")).andExpect(status().isForbidden());
    mvc.perform(post(action).with(user(technician)).with(csrf()).param("version",""+i.getVersion())
        .param("action","RESOLVE").param("details","Premature"))
        .andExpect(status().isBadRequest());
    long oldVersion=i.getVersion();
    mvc.perform(post(action).with(user(technician)).with(csrf()).param("version",""+oldVersion)
        .param("action","ACKNOWLEDGE")).andExpect(redirectedUrl("/incidents/"+i.getId()));
    mvc.perform(post(action).with(user(technician)).with(csrf()).param("version",""+oldVersion)
        .param("action","START")).andExpect(status().isConflict());
    assertEquals(IncidentStatus.ACKNOWLEDGED,i.getStatus());
  }

  @Test
  void unassignedTechnicianAndInactiveActorCannotMutateIncident() {
    var i = create();
    AccountPrincipal second = new AccountPrincipal(accounts.createByAdmin("Second technician",
        "unassigned@workflow.test","Test-password-123",Role.TECHNICIAN,admin.getAccountId()));
    assertThrows(AccessDeniedException.class,()-> incidents.transition(i.getId(),i.getVersion(),"ACKNOWLEDGE","",second));
    assertThrows(AccessDeniedException.class,()-> incidents.transition(i.getId(),i.getVersion(),"ACKNOWLEDGE","",admin));
    jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",employee.getAccountId());
    assertThrows(AccessDeniedException.class,()-> incidents.view(i.getId(),employee));
    assertThrows(AccessDeniedException.class,()-> incidents.create(employee,"Disabled","Details",service,category,1,1,1));
  }

  @Test
  void fullLifecycleWorksThroughRoleAuthenticatedHttpRequests() throws Exception {
    var i = create();
    String actionPath = "/incidents/"+i.getId()+"/action";
    for (AccountPrincipal actor : java.util.List.of(employee, technician, admin)) {
      var login = mvc.perform(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders
          .formLogin("/login").user("email",actor.getUsername()).password("Test-password-123"))
          .andExpect(redirectedUrl("/")).andReturn();
      var session = (org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);
      assertNotNull(session);
      mvc.perform(get("/"+actor.getRole().name().toLowerCase(java.util.Locale.ROOT)+"/dashboard")
          .session(session)).andExpect(status().isOk());
      if (actor.getRole()==Role.TECHNICIAN) {
        for (String action : java.util.List.of("ACKNOWLEDGE","START","RESOLVE"))
          mvc.perform(post(actionPath).session(session).with(csrf()).param("version",""+i.getVersion())
              .param("action",action).param("details","Restored Wi-Fi service"))
              .andExpect(redirectedUrl("/incidents/"+i.getId()));
      }
    }
    mvc.perform(post(actionPath).with(user(employee)).with(csrf()).param("version",""+i.getVersion())
        .param("action","VERIFY_CLOSE")).andExpect(redirectedUrl("/incidents/"+i.getId()));
    assertEquals(IncidentStatus.CLOSED,i.getStatus());
    assertEquals(java.util.List.of("CREATED","CLASSIFIED","ASSIGNED","ACKNOWLEDGED",
        "INVESTIGATION_STARTED","RESOLVED","VERIFIED","CLOSED"),
        jdbc.queryForList("SELECT action FROM audit_entries WHERE incident_id=? ORDER BY id",String.class,i.getId()));
    mvc.perform(post(actionPath).with(user(employee)).with(csrf()).param("version",""+i.getVersion())
        .param("action","REJECT").param("details","Try reopening closed incident"))
        .andExpect(status().isBadRequest());
    assertEquals(IncidentStatus.CLOSED,i.getStatus());
  }

}
