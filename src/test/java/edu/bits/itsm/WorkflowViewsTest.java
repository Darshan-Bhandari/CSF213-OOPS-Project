package edu.bits.itsm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.bits.itsm.identity.*;
import edu.bits.itsm.identity.Role;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.web.DisplayDates;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

class WorkflowViewsTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(Config.class);
    context.refresh();
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void close() {
    context.close();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "dashboard",
        "incidents/list",
        "incidents/new",
        "incidents/detail",
        "notifications",
        "admin/catalog",
        "admin/users",
        "login",
        "register",
        "admin/audit",
        "admin/email"
      })
  void pagesRenderForAdministrator(String view) throws Exception {
    String html = mvc.perform(get("/fixture").param("view", view).param("role", "ADMIN"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    if (view.equals("admin/users")) {
      org.junit.jupiter.api.Assertions.assertTrue(html.contains(">Campus Community</option>"));
      org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"EMPLOYEE\""));
      org.junit.jupiter.api.Assertions.assertTrue(html.contains(">Campus Community</span>"));
    }
    if (view.equals("login"))
      org.junit.jupiter.api.Assertions.assertTrue(html.contains("Create a Campus Community account"));
    if (view.equals("register"))
      org.junit.jupiter.api.Assertions.assertTrue(html.contains("CAMPUS COMMUNITY REGISTRATION"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"EMPLOYEE", "TECHNICIAN"})
  void dashboardsAndDetailsRenderForOtherRoles(String role) throws Exception {
    mvc.perform(get("/fixture").param("view", "dashboard").param("role", role))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(org.hamcrest.Matchers.containsString(">" +
                (role.equals("EMPLOYEE") ? "Campus Community" : "TECHNICIAN") + "</span>")));
    mvc.perform(get("/fixture").param("view", "incidents/detail").param("role", role))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "incidents/list",
        "incidents/detail",
        "notifications",
        "admin/audit",
        "admin/email"
      })
  void populatedPagesEscapeUserContent(String view) throws Exception {
    mvc.perform(
            get("/fixture").param("view", view).param("role", "ADMIN").param("populated", "true"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>unsafe</script>"))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESOLVED", "VERIFIED", "CLOSED"})
  void verificationAndFeedbackBranchesRender(String state) throws Exception {
    mvc.perform(
            get("/fixture")
                .param("view", "incidents/detail")
                .param("role", "EMPLOYEE")
                .param("state", state))
        .andExpect(status().isOk());
    mvc.perform(
            get("/fixture")
                .param("view", "incidents/detail")
                .param("role", "ADMIN")
                .param("state", state))
        .andExpect(status().isOk());
  }

  @Configuration
  @EnableWebMvc
  static class Config {
    @Bean
    SpringResourceTemplateResolver resolver() {
      var r = new SpringResourceTemplateResolver();
      r.setPrefix("classpath:/templates/");
      r.setSuffix(".html");
      r.setTemplateMode("HTML");
      r.setCharacterEncoding("UTF-8");
      return r;
    }

    @Bean
    SpringTemplateEngine engine(SpringResourceTemplateResolver resolver) {
      var e = new SpringTemplateEngine();
      e.setTemplateResolver(resolver);
      return e;
    }

    @Bean
    ThymeleafViewResolver views(SpringTemplateEngine engine) {
      var r = new ThymeleafViewResolver();
      r.setTemplateEngine(engine);
      r.setCharacterEncoding("UTF-8");
      return r;
    }

    @Bean(name = "dates")
    DisplayDates dates() {
      return new DisplayDates();
    }

    @Bean
    FixtureController fixture() {
      return new FixtureController();
    }
  }

  @Controller
  static class FixtureController {
    @GetMapping("/fixture")
    String fixture(
        @RequestParam String view,
        @RequestParam String role,
        @RequestParam(defaultValue = "false") boolean populated,
        @RequestParam(defaultValue = "IN_PROGRESS") String state,
        Model model) {
      var account =
          new UserAccount("Fixture user", "fixture@example.test", "hash", Role.valueOf(role));
      ReflectionTestUtils.setField(account, "id", 2L);
      model.addAttribute("viewer", new AccountPrincipal(account));
      model.addAttribute("form", new IncidentReportForm());
      if (view.equals("register")) model.addAttribute("form", new RegistrationForm());
      if (view.equals("admin/users")) {
        model.addAttribute("form", new AdminAccountForm());
        var community = new UserAccount("Community fixture", "community@example.test", "hash", Role.EMPLOYEE);
        ReflectionTestUtils.setField(community, "id", 3L);
        model.addAttribute("accounts", List.of(community));
      }
      model.addAttribute("messageForm", new IncidentMessageForm());
      model.addAttribute("actionForm", new IncidentActionForm());
      model.addAttribute("feedbackForm", new IncidentFeedbackForm());
      var now = Instant.parse("2026-11-15T10:00:00Z");
      var incident =
          new Incident(
              "Fixture incident",
              "Fixture details",
              2,
              1,
              1,
              10,
              new PriorityAssessment(3, 3, 3),
              now,
              300,
              1800);
      ReflectionTestUtils.setField(incident, "id", 9L);
      incident.transition(IncidentStatus.CLASSIFIED, now);
      incident.assign(2, now);
      incident.transition(IncidentStatus.ACKNOWLEDGED, now);
      incident.transition(IncidentStatus.IN_PROGRESS, now);
      ReflectionTestUtils.setField(incident, "status", IncidentStatus.valueOf(state));
      model.addAttribute("incident", incident);
      model.addAttribute("staff", !role.equals("EMPLOYEE"));
      model.addAttribute("filter", new IncidentFilter());
      model.addAttribute("statuses", IncidentStatus.values());
      model.addAttribute("total", 0L);
      model.addAttribute("rows", List.of());
      model.addAttribute("messages", List.of());
      model.addAttribute("audit", List.of());
      model.addAttribute("events", List.of());
      model.addAttribute("resolutions", List.of());
      model.addAttribute("suggestions", List.of());
      model.addAttribute("feedback", List.of());
      if (populated) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 9L);
        row.put("incident_id", 9L);
        row.put("title", "<script>unsafe</script>");
        row.put("priority", "P1");
        row.put("status", "IN_PROGRESS");
        row.put("service_name", "Campus Wi-Fi");
        row.put("category_name", "Network");
        row.put("technician_name", "Technician");
        row.put("breached", false);
        row.put("created_at", java.sql.Timestamp.from(now));
        row.put("subject", "Update");
        row.put("body", "<script>unsafe</script>");
        row.put("display_name", "Fixture user");
        row.put("kind", "REQUEST");
        row.put("response_to", null);
        row.put("details", "Resolution details");
        row.put("cycle", 1);
        row.put("deadline", java.sql.Timestamp.from(now));
        row.put("action", "CLASSIFIED");
        row.put("actor_name", "System");
        row.put("description", "Details");
        row.put("previous_state", "NEW");
        row.put("new_state", "CLASSIFIED");
        row.put("email", "fixture@example.test");
        row.put("read_at", null);
        row.put("content", "Generated suggestion");
        row.put("model", "test-only-model");
        row.put("category_id", 1L);
        row.put("applied_at", null);
        model.addAttribute("rows", List.of(row));
        model.addAttribute("messages", List.of(row));
        model.addAttribute("audit", List.of(row));
        model.addAttribute("events", List.of(row));
        model.addAttribute("resolutions", List.of(row));
        model.addAttribute("suggestions", List.of(row));
      }
      model.addAttribute("aiConfigured", false);
      model.addAttribute("strategy", "SKILL");
      model.addAttribute("skills", List.of());
      model.addAttribute("policies", List.of());
      model.addAttribute("categoryAnalysis", List.of(Map.of("name", "Network", "total", 1L)));
      model.addAttribute(
          "categories", List.of(Map.of("id", 1L, "name", "Network", "active", true)));
      model.addAttribute(
          "services",
          List.of(Map.of("id", 1L, "name", "Campus Wi-Fi", "criticality", 3, "active", true)));
      model.addAttribute(
          "technicians",
          List.of(
              Map.of(
                  "id",
                  2L,
                  "display_name",
                  "Technician",
                  "active",
                  true,
                  "available",
                  true,
                  "capacity",
                  5,
                  "workload",
                  1L)));
      model.addAttribute(
          "stats",
          Map.ofEntries(
              Map.entry("total", 1L),
              Map.entry("backlog", 1L),
              Map.entry("critical", 1L),
              Map.entry("breaches", 0L),
              Map.entry("avg_response", 5),
              Map.entry("avg_resolution", 20),
              Map.entry("response_samples", 1L),
              Map.entry("resolution_samples", 1L),
              Map.entry("sla_eligible", 1L),
              Map.entry("sla_met", 1L)));
      return view;
    }
  }
}
