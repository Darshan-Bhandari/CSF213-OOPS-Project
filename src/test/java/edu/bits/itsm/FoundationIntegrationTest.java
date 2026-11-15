package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.bits.itsm.identity.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FoundationIntegrationTest {
  @AfterAll
  static void stopDatabase() throws java.io.IOException {
    POSTGRES.close();
  }

  static final EmbeddedPostgres POSTGRES = startPostgres();

  static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder().setPort(0).start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }

  @Autowired MockMvc mvc;
  @Autowired AccountService service;
  @Autowired UserAccountRepository accounts;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;
  UserAccount employee, admin, technician;
  static final String PASSWORD = "Example-Only-123";

  @BeforeEach
  void accounts() {
    employee = service.register("Employee", "employee@example.test", PASSWORD);
    admin =
        accounts.saveAndFlush(
            new UserAccount("Admin", "admin@example.test", encoder.encode(PASSWORD), Role.ADMIN));
    technician =
        service.createByAdmin(
            "Technician", "technician@example.test", PASSWORD, Role.TECHNICIAN, admin.getId());
  }

  @Test
  void flywayMigratesRealPostgresAndSeedsCatalog() {
    assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM it_services", Integer.class));
    assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class));
    assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM sla_policies", Integer.class));
    assertTrue(jdbc.queryForObject("SELECT version()", String.class).startsWith("PostgreSQL"));
  }

  @Test
  void unauthenticatedDashboardRequiresLogin() throws Exception {
    mvc.perform(get("/employee/dashboard"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @Test
  void publicPagesRenderAndIncludeCsrf() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
    mvc.perform(get("/register")).andExpect(status().isOk());
  }

  @Test
  void registrationCannotSelectAdministratorRole() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", "New employee")
                .param("email", "NEW@example.test")
                .param("password", PASSWORD)
                .param("role", "ADMIN"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));
    var created = accounts.findByEmail("new@example.test").orElseThrow();
    assertEquals(Role.EMPLOYEE, created.getRole());
    assertNotEquals(PASSWORD, created.getPasswordHash());
    assertTrue(encoder.matches(PASSWORD, created.getPasswordHash()));
  }

  @Test
  void missingCsrfRejectsRegistrationAndDoesNotPersist() throws Exception {
    mvc.perform(
            post("/register")
                .param("displayName", "User")
                .param("email", "blocked@example.test")
                .param("password", PASSWORD))
        .andExpect(status().isForbidden());
    assertFalse(accounts.existsByEmail("blocked@example.test"));
  }

  @Test
  void duplicateEmailIsNormalizedAndRejected() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", "Duplicate")
                .param("email", "EMPLOYEE@example.test")
                .param("password", PASSWORD))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasErrors("form"));
  }

  @Test
  void invalidInputDoesNotPersist() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("displayName", "")
                .param("email", "invalid")
                .param("password", "short"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasErrors("form"));
  }

  @Test
  void validLoginAndInvalidPassword() throws Exception {
    mvc.perform(formLogin("/login").user("email", employee.getEmail()).password(PASSWORD))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
    mvc.perform(formLogin("/login").user("email", employee.getEmail()).password("wrong"))
        .andExpect(redirectedUrl("/login?error"));
  }

  @Test
  void threeRoleDashboardsRenderWithoutAiKey() throws Exception {
    for (var account : java.util.List.of(employee, technician, admin)) {
      mvc.perform(
              get("/" + account.getRole().name().toLowerCase(java.util.Locale.ROOT) + "/dashboard")
                  .with(user(new AccountPrincipal(account))))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("Not configured")));
    }
  }

  @Test
  void employeeCannotReadAdminDirectoryOrPostAccount() throws Exception {
    var principal = new AccountPrincipal(employee);
    mvc.perform(get("/admin/users").with(user(principal))).andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/users")
                .with(user(principal))
                .with(csrf())
                .param("displayName", "Intruder")
                .param("email", "intruder@example.test")
                .param("password", PASSWORD)
                .param("role", "ADMIN"))
        .andExpect(status().isForbidden());
    assertFalse(accounts.existsByEmail("intruder@example.test"));
  }

  @Test
  void adminDirectoryRendersAndCreatesTechnicianWithProfileAndAudit() throws Exception {
    mvc.perform(get("/admin/users").with(user(new AccountPrincipal(admin))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/admin/users")
                .with(user(new AccountPrincipal(admin)))
                .with(csrf())
                .param("displayName", "Second technician")
                .param("email", "second@example.test")
                .param("password", PASSWORD)
                .param("role", "TECHNICIAN"))
        .andExpect(redirectedUrl("/admin/users"));
    var created = accounts.findByEmail("second@example.test").orElseThrow();
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM technician_profiles WHERE user_id=?",
            Integer.class,
            created.getId()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_entries WHERE subject=? AND actor_id=?",
            Integer.class,
            "USER-" + created.getId(),
            admin.getId()));
  }

  @Test
  void deactivationDeniesLoginAndRevokesExistingSession() throws Exception {
    var result =
        mvc.perform(formLogin("/login").user("email", employee.getEmail()).password(PASSWORD))
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertNotNull(session);
    service.setActive(employee.getId(), false, admin.getId());
    accounts.flush();
    mvc.perform(get("/employee/dashboard").session(session))
        .andExpect(redirectedUrl("/login?expired"));
    mvc.perform(formLogin("/login").user("email", employee.getEmail()).password(PASSWORD))
        .andExpect(redirectedUrl("/login?error"));
  }

  @Test
  void administratorCannotDeactivateThemselves() {
    assertThrows(
        edu.bits.itsm.shared.BusinessException.class,
        () -> service.setActive(admin.getId(), false, admin.getId()));
  }

  @Test
  void serviceRejectsNonAdminProvisioning() {
    assertThrows(
        edu.bits.itsm.shared.BusinessException.class,
        () ->
            service.createByAdmin("No", "no@example.test", PASSWORD, Role.ADMIN, employee.getId()));
  }

  @Test
  void csrfProtectedLogoutInvalidatesSession() throws Exception {
    var result =
        mvc.perform(formLogin("/login").user("email", employee.getEmail()).password(PASSWORD))
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    mvc.perform(post("/logout").session(session).with(csrf()))
        .andExpect(redirectedUrl("/login?logout"));
    assertTrue(session.isInvalid());
  }
  @Test
  void incompleteDirectRegistrationIsRejectedWithoutPartialAccounts() {
    long before=accounts.count();
    for(String[] input:java.util.List.of(new String[]{null,"null-name@example.test",PASSWORD},
        new String[]{"Name",null,PASSWORD},new String[]{"Name","null-password@example.test",null}))
      assertThrows(edu.bits.itsm.shared.BusinessException.class,()->service.register(input[0],input[1],input[2]));
    assertEquals(before,accounts.count());
  }

}
