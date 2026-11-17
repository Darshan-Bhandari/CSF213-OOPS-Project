package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.bits.itsm.ai.*;
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
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"app.sla.monitor-enabled=false", "app.ai.api-key=test-only-no-live-key", "app.ai.model=test-only-model"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class IntelligenceReportingIntegrationTest {
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
    @Bean @Primary ControlledClock intelligenceClock() { return new ControlledClock(); }
  }
  static class ControlledClock extends Clock {
    Instant now = Instant.parse("2026-11-17T00:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
    void advance(long seconds) { now = now.plusSeconds(seconds); }
  }
  @MockitoBean AiTransport transport;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ControlledClock clock;
  @Autowired UserAccountRepository users;
  @Autowired AccountService accounts;
  @Autowired PasswordEncoder encoder;
  @Autowired AssignmentService assignment;
  @Autowired IncidentService incidents;
  @Autowired IncidentAiAdvisor advisor;
  @Autowired IncidentQueryService queries;
  @Autowired SlaMonitor monitor;
  @Autowired CatalogService catalog;
  AccountPrincipal admin, employee, technician, other;
  long service, category;

  @BeforeEach void fixture() {
    clock.now = Instant.parse("2026-11-17T00:00:00Z");
    admin = new AccountPrincipal(users.saveAndFlush(new UserAccount("Intelligence admin", "admin@intelligence.test",
        encoder.encode("Test-password-123"), Role.ADMIN)));
    employee = new AccountPrincipal(accounts.register("Reporter", "reporter@intelligence.test", "Test-password-123"));
    other = new AccountPrincipal(accounts.register("Other", "other@intelligence.test", "Test-password-123"));
    technician = new AccountPrincipal(accounts.createByAdmin("Technician", "tech@intelligence.test", "Test-password-123", Role.TECHNICIAN, admin.getAccountId()));
    category = jdbc.queryForObject("SELECT id FROM categories WHERE name='Network'", Long.class);
    service = jdbc.queryForObject("SELECT id FROM it_services WHERE name='Campus Wi-Fi'", Long.class);
    assignment.skill(technician.getAccountId(), category, 3, admin.getAccountId());
    assignment.technician(technician.getAccountId(), true, 100, admin.getAccountId());
  }
  Incident create() { return incidents.create(employee, "Library Wi-Fi failure", "Cannot connect",
      service, category, 3, 3, 10); }
  void action(Incident i, String action, AccountPrincipal actor) {
    incidents.transition(i.getId(), i.getVersion(), action, "Acceptance details", actor);
  }
  void close(Incident i, String details) {
    action(i, "ACKNOWLEDGE", technician); action(i, "START", technician);
    incidents.transition(i.getId(), i.getVersion(), "RESOLVE", details, technician);
    action(i, "VERIFY_CLOSE", employee);
  }
  long suggestion(Incident i) {
    return jdbc.queryForObject("SELECT max(id) FROM ai_suggestions WHERE incident_id=?", Long.class, i.getId());
  }
  long rows(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

  @Test void categorySuggestionRequiresExplicitApplicationAndNeverMovesLifecycleOrPriority() {
    var i = create(); long version = i.getVersion();
    when(transport.complete(anyMap())).thenReturn("{\"categoryId\":" + category + ",\"explanation\":\"Network symptoms\"}");
    advisor.suggest(i.getId(), version, "CATEGORY", technician);
    assertEquals(version, i.getVersion()); assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
    assertEquals(1, rows("ai_suggestions"));
    incidents.applySuggestion(i.getId(), version, suggestion(i), technician);
    assertEquals(category, i.getCategoryId()); assertEquals("P1", i.getAssessment().getPriority());
    assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
    assertNotNull(jdbc.queryForObject("SELECT applied_at FROM ai_suggestions WHERE id=?", java.sql.Timestamp.class, suggestion(i)));
    assertThrows(BusinessException.class, () -> incidents.applySuggestion(i.getId(), i.getVersion(), suggestion(i), technician));
  }

  @ParameterizedTest @ValueSource(strings = {"not JSON", "{}", "{\"categoryId\":1.5,\"explanation\":\"Fractional\"}",
      "{\"categoryId\":9223372036854775808,\"explanation\":\"Overflow\"}", "{\"categoryId\":999999,\"explanation\":\"Unknown\"}",
      "{\"categoryId\":1,\"explanation\":\" \"}"})
  void invalidCategoryOutputDoesNotSaveOrMutateIncident(String answer) {
    var i = create(); long version = i.getVersion(), audits = rows("audit_entries");
    when(transport.complete(anyMap())).thenReturn(answer);
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), version, "CATEGORY", technician));
    assertEquals(0, rows("ai_suggestions")); assertEquals(audits, rows("audit_entries"));
    assertEquals(version, i.getVersion()); assertEquals(category, i.getCategoryId());
  }

  @Test void inactiveCategoryAndMissingRequiredSkillCannotBeApplied() {
    var i = create(); long hardware = jdbc.queryForObject("SELECT id FROM categories WHERE name='Hardware'", Long.class);
    when(transport.complete(anyMap())).thenReturn("{\"categoryId\":" + hardware + ",\"explanation\":\"Hardware symptoms\"}");
    advisor.suggest(i.getId(), i.getVersion(), "CATEGORY", technician);
    assertThrows(BusinessException.class, () -> incidents.applySuggestion(i.getId(), i.getVersion(), suggestion(i), technician));
    assertEquals(category, i.getCategoryId());
    catalog.saveCategory(hardware, "Hardware", false, admin.getAccountId());
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "CATEGORY", admin));
    assertEquals(1, rows("ai_suggestions"));
  }

  @Test void resolutionUsesOnlyFinalClosedRelevantHistoryAndProducesAnEditableDraft() throws Exception {
    var historical = create();
    action(historical, "ACKNOWLEDGE", technician); action(historical, "START", technician);
    incidents.transition(historical.getId(), historical.getVersion(), "RESOLVE", "REJECTED historical fix", technician);
    action(historical, "REJECT", employee); close(historical, "Accepted historical fix");
    var unresolved = create();
    action(unresolved, "ACKNOWLEDGE", technician); action(unresolved, "START", technician);
    incidents.transition(unresolved.getId(), unresolved.getVersion(), "RESOLVE", "UNVERIFIED historical fix", technician);
    var i = create();
    when(transport.complete(anyMap())).thenReturn("Check the access point safely.");
    advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", technician);
    @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> request = ArgumentCaptor.forClass(Map.class);
    verify(transport).complete(request.capture());
    String prompt = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.getValue());
    assertTrue(prompt.contains("Accepted historical fix")); assertFalse(prompt.contains("REJECTED historical fix"));
    assertFalse(prompt.contains("UNVERIFIED historical fix"));
    assertFalse(prompt.contains(employee.getUsername())); assertFalse(prompt.contains("test-only-no-live-key"));
    incidents.applySuggestion(i.getId(), i.getVersion(), suggestion(i), technician);
    assertTrue(i.getResolutionDraft().contains("Historical records (retrieved"));
    assertTrue(i.getResolutionDraft().contains("Generated advice (unverified"));
    assertTrue(i.getResolutionDraft().contains("INC-" + historical.getId()));
    assertEquals(IncidentStatus.ASSIGNED, i.getStatus()); assertNull(i.getResolvedAt());
  }

  @Test void noHistoryIsExplicitAndOversizedResolutionIsRejected() {
    var i = create(); when(transport.complete(anyMap())).thenReturn("Review connectivity.");
    advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", admin);
    String content = jdbc.queryForObject("SELECT content FROM ai_suggestions WHERE id=?", String.class, suggestion(i));
    assertTrue(content.contains("No matching closed incident records."));
    when(transport.complete(anyMap())).thenReturn("x".repeat(6001));
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", technician));
    assertEquals(1, rows("ai_suggestions"));
  }

  @Test void staleProviderResponseAndStaleApplicationAreRejected() {
    var i = create(); long version = i.getVersion();
    when(transport.complete(anyMap())).thenAnswer(call -> {
      clock.advance(1); incidents.message(i.getId(), i.getVersion(), "PUBLIC", "New information", null, employee);
      return "Review new information.";
    });
    assertThrows(ConflictException.class, () -> advisor.suggest(i.getId(), version, "RESOLUTION", technician));
    assertEquals(0, rows("ai_suggestions"));
    doReturn("Review new information.").when(transport).complete(anyMap());
    advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", technician);
    clock.advance(1); incidents.message(i.getId(), i.getVersion(), "PUBLIC", "Another update", null, employee);
    assertThrows(ConflictException.class, () -> incidents.applySuggestion(i.getId(), i.getVersion(), suggestion(i), technician));
    assertEquals("", i.getResolutionDraft());
  }

  @Test void providerFailureLeavesCrudWorkingAndNoPartialSuggestion() {
    var i = create(); when(transport.complete(anyMap())).thenThrow(new BusinessException("AI request timed out"));
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", technician));
    assertEquals(0, rows("ai_suggestions"));
    incidents.message(i.getId(), i.getVersion(), "PUBLIC", "Manual work continues", null, employee);
    close(i, "Manual solution"); assertEquals(IncidentStatus.CLOSED, i.getStatus());
  }

  @Test void missingKeyKeepsAdvisorUnavailableWithoutCallingTransport() {
    var i = create();
    var disabled = new IncidentAiAdvisor(incidents, catalog, transport, new AiConfiguration("", ""),
        new com.fasterxml.jackson.databind.ObjectMapper(), null, jdbc);
    assertThrows(BusinessException.class, () -> disabled.suggest(i.getId(), i.getVersion(), "RESOLUTION", technician));
    verifyNoInteractions(transport); assertEquals(0, rows("ai_suggestions"));
    close(i, "Manual resolution without AI"); assertEquals(IncidentStatus.CLOSED, i.getStatus());
  }

  @Test void unauthorizedClosedAndInvalidRequestsNeverCallProvider() {
    var i = create();
    assertThrows(AccessDeniedException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", employee));
    assertThrows(AccessDeniedException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", other));
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), i.getVersion(), null, technician));
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "UNKNOWN", technician));
    close(i, "Manual solution");
    assertThrows(BusinessException.class, () -> advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", admin));
    verifyNoInteractions(transport);
  }

  @Test void renderedSuggestionIsEscapedAndEmployeeCannotReadOrApplyIt() throws Exception {
    var i = create(); when(transport.complete(anyMap())).thenReturn("<script>alert('untrusted')</script>");
    advisor.suggest(i.getId(), i.getVersion(), "RESOLUTION", technician);
    String staff = mvc.perform(get("/incidents/" + i.getId()).with(user(technician))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(staff.contains("&lt;script&gt;")); assertFalse(staff.contains("<script>alert"));
    String reporter = mvc.perform(get("/incidents/" + i.getId()).with(user(employee))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(reporter.contains("untrusted")); assertFalse(staff.contains("test-only-no-live-key"));
    mvc.perform(post("/incidents/" + i.getId() + "/ai/apply").with(user(employee)).with(csrf())
        .param("version", "" + i.getVersion()).param("suggestion", "" + suggestion(i))).andExpect(status().isForbidden());
    mvc.perform(post("/incidents/" + i.getId() + "/ai").with(user(technician))
        .param("version", "" + i.getVersion()).param("kind", "RESOLUTION")).andExpect(status().isForbidden());
  }

  @Test void eightCombinedFilterCriteriaAndRoleScopeMatchStoredRecords() {
    var expected = create();
    incidents.create(other, "Library Wi-Fi failure", "Cannot connect", service, category, 3, 3, 10);
    incidents.create(employee, "Other problem", "Different symptoms", service, category, 1, 1, 1);
    clock.advance(240); monitor.scan();
    var f = new IncidentFilter(); f.setQuery("INC-" + expected.getId()); f.setStatus("ASSIGNED");
    f.setPriority("P1"); f.setCategory(category); f.setService(service); f.setTechnician(technician.getAccountId());
    f.setFrom(LocalDate.of(2026,11,17)); f.setTo(LocalDate.of(2026,11,17)); f.setSla("WARNING");
    assertEquals(1, queries.count(admin, f));
    assertEquals(expected.getId(), ((Number)queries.list(employee, f).getFirst().get("id")).longValue());
    assertEquals(0, queries.count(other, f)); assertEquals(1, queries.count(technician, f));
    f.setPriority("P2"); assertEquals(0, queries.count(admin, f));
    f.setPriority("P1"); f.setSla("BREACHED"); assertEquals(0, queries.count(admin, f));
    clock.advance(61); monitor.scan(); assertEquals(1, queries.count(admin, f));
    assertEquals(1L, ((Number)queries.metrics(admin,f).get("total")).longValue());
    assertEquals(1L, ((Number)queries.byCategory(admin,f).getFirst().get("total")).longValue());
  }

  @Test void dateFiltersUseInclusiveIndianCalendarDays() {
    clock.now = Instant.parse("2026-11-16T18:29:59Z"); create();
    clock.advance(1); var first = create();
    clock.now = Instant.parse("2026-11-17T18:29:59Z"); var last = create();
    clock.advance(1); create();
    var f = new IncidentFilter(); f.setFrom(LocalDate.of(2026,11,17)); f.setTo(LocalDate.of(2026,11,17));
    assertEquals(Set.of(first.getId(), last.getId()), queries.list(employee,f).stream()
        .map(r -> ((Number)r.get("id")).longValue()).collect(java.util.stream.Collectors.toSet()));
  }

  @Test void paginationIsStableScopedAndCountIsIndependentOfPage() {
    for (int n=0;n<21;n++) create();
    incidents.create(other,"Someone else's ticket","Private scope",service,category,3,3,1);
    var f = new IncidentFilter(); var first = queries.list(employee,f); f.setPage(1); var second = queries.list(employee,f);
    assertEquals(20, first.size()); assertEquals(1, second.size()); assertEquals(21,queries.count(employee,f));
    assertFalse(first.stream().anyMatch(r -> r.get("id").equals(second.getFirst().get("id"))));
    assertTrue(((Number)first.getLast().get("id")).longValue() > ((Number)second.getFirst().get("id")).longValue());
    f.setQuery("' OR 1=1 --"); assertEquals(0,queries.count(employee,f));
  }

  @Test void metricsHaveKnownAveragesDenominatorsAndZeroDataBehavior() {
    var empty = queries.metrics(employee,new IncidentFilter());
    assertEquals(0, ((Number)empty.get("total")).intValue()); assertNull(empty.get("avg_response")); assertNull(empty.get("avg_resolution"));
    var completed = create(); clock.advance(60); action(completed,"ACKNOWLEDGE",technician); action(completed,"START",technician);
    clock.advance(120); incidents.transition(completed.getId(), completed.getVersion(), "RESOLVE", "Restored", technician);
    action(completed,"VERIFY_CLOSE",employee);
    var late = create(); clock.advance(400); action(late,"ACKNOWLEDGE",technician);
    create(); monitor.scan();
    var m = queries.metrics(admin,new IncidentFilter());
    assertEquals(3, ((Number)m.get("total")).intValue()); assertEquals(2, ((Number)m.get("backlog")).intValue());
    assertEquals(2, ((Number)m.get("critical")).intValue()); assertEquals(1, ((Number)m.get("closed")).intValue());
    assertEquals(1, ((Number)m.get("breaches")).intValue()); assertEquals(2, ((Number)m.get("response_samples")).intValue());
    assertEquals(1, ((Number)m.get("resolution_samples")).intValue());
    assertEquals(3.8, ((Number)m.get("avg_response")).doubleValue()); assertEquals(3.0, ((Number)m.get("avg_resolution")).doubleValue());
    assertEquals(2, ((Number)m.get("sla_eligible")).intValue()); assertEquals(1, ((Number)m.get("sla_met")).intValue());
  }

  @Test void reportingServiceRejectsDeactivatedAccountsAndNullPrincipals() {
    create(); jdbc.update("UPDATE user_accounts SET active=false WHERE id=?", employee.getAccountId());
    assertThrows(AccessDeniedException.class, () -> queries.list(employee,new IncidentFilter()));
    assertThrows(AccessDeniedException.class, () -> queries.metrics(employee,new IncidentFilter()));
    assertThrows(AccessDeniedException.class, () -> queries.byCategory(employee,new IncidentFilter()));
    assertThrows(AccessDeniedException.class, () -> queries.count(null,new IncidentFilter()));
  }
}
