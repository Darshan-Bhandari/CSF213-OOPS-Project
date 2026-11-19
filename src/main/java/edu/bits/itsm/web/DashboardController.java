package edu.bits.itsm.web;

import edu.bits.itsm.ai.AiConfiguration;
import edu.bits.itsm.identity.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private final JdbcTemplate jdbc;
  private final AiConfiguration ai;
  private final edu.bits.itsm.reporting.IncidentQueryService queries;
  private final edu.bits.itsm.catalog.CatalogService catalog;
  private final edu.bits.itsm.assignment.AssignmentService assignment;

  public DashboardController(JdbcTemplate jdbc, AiConfiguration ai,
      edu.bits.itsm.reporting.IncidentQueryService queries,
      edu.bits.itsm.catalog.CatalogService catalog,
      edu.bits.itsm.assignment.AssignmentService assignment) {
    this.queries = queries;
    this.catalog = catalog;
    this.assignment = assignment;
    this.jdbc = jdbc;
    this.ai = ai;
  }

  @GetMapping("/")
  String home(@AuthenticationPrincipal AccountPrincipal principal) {
    return "redirect:/"
        + principal.getRole().name().toLowerCase(java.util.Locale.ROOT)
        + "/dashboard";
  }

  @GetMapping({"/employee/dashboard", "/technician/dashboard", "/admin/dashboard"})
  String dashboard(@AuthenticationPrincipal AccountPrincipal actor,
      @org.springframework.web.bind.annotation.ModelAttribute("filter") edu.bits.itsm.incident.IncidentFilter filter,
      Model model) {
    model.addAttribute("categories", catalog.categories());
    model.addAttribute("services", catalog.services());
    model.addAttribute("technicians", assignment.technicians());
    model.addAttribute("statuses", edu.bits.itsm.incident.IncidentStatus.values());
    model.addAttribute("stats", queries.metrics(actor, filter));
    model.addAttribute("categoryAnalysis", queries.byCategory(actor, filter));
    model.addAttribute("recentIncidents", queries.list(actor, filter).stream().limit(4).toList());
    model.addAttribute(
        "serviceCount",
        jdbc.queryForObject("SELECT COUNT(*) FROM it_services WHERE active", Long.class));
    model.addAttribute(
        "categoryCount",
        jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE active", Long.class));
    model.addAttribute(
        "policyCount", jdbc.queryForObject("SELECT COUNT(*) FROM sla_policies", Long.class));
    model.addAttribute("aiConfigured", ai.isConfigured());
    return "dashboard";
  }

  @org.springframework.web.bind.annotation.RequestMapping("/access-denied")
  String denied(HttpServletResponse response) {
    response.setStatus(403);
    return "error/403";
  }
}
