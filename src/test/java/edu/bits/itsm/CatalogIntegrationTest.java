package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.shared.BusinessException;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.sla.monitor-enabled=false")
@AutoConfigureMockMvc
@Transactional
class CatalogIntegrationTest {
  static final EmbeddedPostgres POSTGRES = start();
  static EmbeddedPostgres start() {
    try { return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").setPort(0).start(); }
    catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
  }
  @AfterAll static void close() throws Exception { POSTGRES.close(); }
  @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }
  @Autowired CatalogService catalog;
  @Autowired edu.bits.itsm.assignment.AssignmentService assignment;
  @Autowired UserAccountRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  UserAccount admin, employee;
  @BeforeEach void accounts() {
    admin = users.saveAndFlush(new UserAccount("Catalog admin", "catalog-admin@example.test", "unused-test-hash", Role.ADMIN));
    employee = users.saveAndFlush(new UserAccount("Catalog employee", "catalog-user@example.test", "unused-test-hash", Role.EMPLOYEE));
  }
  long id(String table, String name) {
    return jdbc.queryForObject("SELECT id FROM " + table + " WHERE name=?", Long.class, name);
  }
  @Test void adminAddsAndEditsCatalogWithRealAuditSubjects() throws Exception {
    mvc.perform(get("/admin/catalog").with(user(new AccountPrincipal(admin))))
        .andExpect(status().isOk());
    mvc.perform(post("/admin/catalog/services").with(user(new AccountPrincipal(admin))).with(csrf())
        .param("name", "  Lecture capture  ").param("criticality", "2").param("active", "true"))
        .andExpect(redirectedUrl("/admin/catalog"));
    long service = id("it_services", "Lecture capture");
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_entries WHERE subject=? AND actor_id=?",
        Integer.class, "SERVICE-" + service, admin.getId()));
    catalog.saveService(service, "Lecture recording", 3, false, admin.getId());
    assertThrows(BusinessException.class, () -> catalog.service(service));
    catalog.saveService(service, "Lecture recording", 1, true, admin.getId());
    assertEquals(1, catalog.service(service).get("criticality"));
    catalog.saveCategory(null, "Streaming", true, admin.getId());
    long category = id("categories", "Streaming");
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_entries WHERE subject=?", Integer.class, "CATEGORY-" + category));
    catalog.saveCategory(category, "Streaming support", false, admin.getId());
    assertThrows(BusinessException.class, () -> catalog.requireCategory(category));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM categories WHERE id=?", Integer.class, category));
  }
  @Test void employeeAndTechnicianCannotAccessCatalogOrMutateIt() throws Exception {
    for (Role role : new Role[]{Role.EMPLOYEE, Role.TECHNICIAN}) {
      var actor = users.saveAndFlush(new UserAccount("Other role", role.name().toLowerCase()+"-catalog@example.test", "unused", role));
      mvc.perform(get("/admin/catalog").with(user(new AccountPrincipal(actor)))).andExpect(status().isForbidden());
      for (String endpoint : new String[]{"services", "categories"})
        mvc.perform(post("/admin/catalog/"+endpoint).with(user(new AccountPrincipal(actor))).with(csrf())
            .param("name", "Forbidden").param("criticality", "1")).andExpect(status().isForbidden());
    }
  }
  @Test void serviceBoundaryRejectsNonAdminAndInactiveAdmin() {
    assertThrows(AccessDeniedException.class, () -> catalog.saveCategory(null,"Forbidden",true,employee.getId()));
    assertThrows(AccessDeniedException.class, () -> catalog.saveService(null,"Forbidden",1,true,employee.getId()));
    jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",admin.getId());
    assertThrows(AccessDeniedException.class, () -> catalog.saveCategory(null,"Forbidden",true,admin.getId()));
  }
  @Test void adminMutationRequiresCsrf() throws Exception {
    mvc.perform(post("/admin/catalog/categories").with(user(new AccountPrincipal(admin))).param("name","No CSRF"))
        .andExpect(status().isForbidden());
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM categories WHERE name='No CSRF'", Integer.class));
  }
  @Test void duplicateNameGetsUsefulErrorAndNoExtraRow() throws Exception {
    mvc.perform(post("/admin/catalog/categories").with(user(new AccountPrincipal(admin))).with(csrf())
        .param("name","  nEtWoRk  ").param("active","true"))
        .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM categories WHERE lower(trim(name))='network'",Integer.class));
  }
  @Test void invalidServiceInputsAndMissingIdsDoNotWriteAudit() {
    long before = jdbc.queryForObject("SELECT count(*) FROM audit_entries", Long.class);
    assertThrows(BusinessException.class, () -> catalog.saveService(null," ",1,true,admin.getId()));
    assertThrows(BusinessException.class, () -> catalog.saveService(null,"x".repeat(101),1,true,admin.getId()));
    assertThrows(BusinessException.class, () -> catalog.saveService(null,"Invalid",0,true,admin.getId()));
    assertThrows(BusinessException.class, () -> catalog.saveService(null,"Invalid",4,true,admin.getId()));
    assertThrows(BusinessException.class, () -> catalog.saveService(Long.MAX_VALUE,"Missing",1,true,admin.getId()));
    assertThrows(BusinessException.class, () -> catalog.saveCategory(Long.MAX_VALUE,"Missing",true,admin.getId()));
    assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM audit_entries",Long.class));
  }
  @Test void assignmentConfigurationRequiresActiveAdminAtServiceBoundary() {
    assertThrows(AccessDeniedException.class,()->assignment.configure("LOAD",employee.getId()));
    assertThrows(AccessDeniedException.class,()->assignment.technician(-1,true,5,employee.getId()));
    assertThrows(AccessDeniedException.class,()->assignment.skill(-1,1,2,employee.getId()));
    jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",admin.getId());
    assertThrows(AccessDeniedException.class,()->assignment.configure("LOAD",admin.getId()));
    assertEquals("SKILL",assignment.selected());
  }

}
