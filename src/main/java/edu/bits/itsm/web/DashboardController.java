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

  public DashboardController(JdbcTemplate jdbc, AiConfiguration ai) {
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
  String dashboard(Model model) {
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

  @GetMapping("/access-denied")
  String denied(HttpServletResponse response) {
    response.setStatus(403);
    return "error/403";
  }
}
