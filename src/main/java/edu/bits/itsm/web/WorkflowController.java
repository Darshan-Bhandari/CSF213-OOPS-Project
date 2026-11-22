package edu.bits.itsm.web;

import edu.bits.itsm.ai.*;
import edu.bits.itsm.assignment.AssignmentService;
import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.notification.NotificationService;
import edu.bits.itsm.reporting.IncidentQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkflowController {
  private final IncidentService incidents;
  private final IncidentHistory history;
  private final CatalogService catalog;
  private final AssignmentService assignment;
  private final IncidentQueryService queries;
  private final NotificationService notifications;
  private final AiAdvisor ai;
  private final AiConfiguration config;

  public WorkflowController(
      IncidentService incidents,
      IncidentHistory history,
      CatalogService catalog,
      AssignmentService assignment,
      IncidentQueryService queries,
      NotificationService notifications,
      AiAdvisor ai,
      AiConfiguration config) {
    this.incidents = incidents;
    this.history = history;
    this.catalog = catalog;
    this.assignment = assignment;
    this.queries = queries;
    this.notifications = notifications;
    this.ai = ai;
    this.config = config;
  }

  private void catalog(Model model) {
    model.addAttribute("categories", catalog.categories());
    model.addAttribute("services", catalog.services());
    model.addAttribute("technicians", assignment.technicians());
    model.addAttribute("statuses", IncidentStatus.values());
  }

  @GetMapping("/incidents")
  String list(
      @AuthenticationPrincipal AccountPrincipal actor,
      @ModelAttribute("filter") IncidentFilter filter,
      Model model) {
    catalog(model);
    model.addAttribute("rows", queries.list(actor, filter));
    model.addAttribute("total", queries.count(actor, filter));
    return "incidents/list";
  }

  @GetMapping("/employee/incidents/new")
  String createForm(@ModelAttribute("form") IncidentReportForm form, Model model) {
    catalog(model);
    return "incidents/new";
  }

  @PostMapping("/employee/incidents")
  String create(
      @AuthenticationPrincipal AccountPrincipal actor,
      @jakarta.validation.Valid @ModelAttribute("form") IncidentReportForm form,
      org.springframework.validation.BindingResult errors,
      Model model,
      jakarta.servlet.http.HttpServletResponse response) {
    if (!errors.hasErrors()) {
      try {
        return "redirect:/incidents/" + incidents.create(actor, form.getTitle(),
            form.getDescription(), form.getService(), form.getCategory(), form.getImpact(),
            form.getUrgency(), form.getAffected()).getId();
      } catch (edu.bits.itsm.shared.BusinessException ex) {
        errors.reject("incident", ex.getMessage());
      }
    }
    response.setStatus(400);
    catalog(model);
    return "incidents/new";
  }

  @GetMapping("/incidents/{id}")
  String detail(
      @PathVariable long id, @AuthenticationPrincipal AccountPrincipal actor, Model model) {
    var i = incidents.view(id, actor);
    catalog(model);
    model.addAttribute("incident", i);
    if (!model.containsAttribute("actionForm")) {
      var form = new IncidentActionForm();
      form.setVersion(i.getVersion());
      model.addAttribute("actionForm", form);
    }
    if (!model.containsAttribute("feedbackForm"))
      model.addAttribute("feedbackForm", new IncidentFeedbackForm());
    if (!model.containsAttribute("messageForm")) {
      var form = new IncidentMessageForm();
      form.setVersion(i.getVersion());
      model.addAttribute("messageForm", form);
    }
    model.addAttribute("staff", actor.getRole() != Role.EMPLOYEE);
    if (actor.getRole() != Role.EMPLOYEE)
      model.addAttribute("correlation", incidents.correlations(id, actor));
    model.addAttribute("messages", history.messages(id, actor));
    model.addAttribute("audit", history.audit(id, actor));
    model.addAttribute("resolutions", history.resolutions(id, actor));
    model.addAttribute("events", history.events(id, actor));
    model.addAttribute("feedback", history.feedback(id, actor));
    model.addAttribute(
        "suggestions",
        actor.getRole() == Role.EMPLOYEE ? java.util.List.of() : history.suggestions(id, actor));
    model.addAttribute("aiConfigured", config.isConfigured());
    return "incidents/detail";
  }

  @PostMapping("/incidents/{id}/duplicate")
  String duplicate(
      @PathVariable long id,
      @RequestParam long master,
      @AuthenticationPrincipal AccountPrincipal actor,
      RedirectAttributes flash) {
    boolean linked = incidents.linkDuplicate(id, master, actor);
    flash.addFlashAttribute("success", linked
        ? "Duplicate report linked to INC-" + master + "."
        : "This report is already linked to INC-" + master + ".");
    return back(id);
  }

  @PostMapping("/incidents/{id}/action")
  String action(
      @PathVariable long id,
      @jakarta.validation.Valid @ModelAttribute("actionForm") IncidentActionForm form,
      org.springframework.validation.BindingResult errors,
      @AuthenticationPrincipal AccountPrincipal actor,
      Model model,
      jakarta.servlet.http.HttpServletResponse response) {
    int status = 400;
    if (!errors.hasErrors()) {
      try {
        incidents.transition(id, form.getVersion(), form.getAction(), form.getDetails(), actor);
        return back(id);
      } catch (edu.bits.itsm.shared.BusinessException ex) {
        errors.reject("action", ex.getMessage());
        if (ex instanceof edu.bits.itsm.shared.ConflictException) status = 409;
      }
    }
    response.setStatus(status);
    return detail(id, actor, model);
  }

  @PostMapping("/incidents/{id}/messages")
  String message(
      @PathVariable long id,
      @jakarta.validation.Valid @ModelAttribute("messageForm") IncidentMessageForm form,
      org.springframework.validation.BindingResult errors,
      @AuthenticationPrincipal AccountPrincipal actor,
      Model model,
      jakarta.servlet.http.HttpServletResponse response) {
    int status = 400;
    if (!errors.hasErrors()) {
      try {
        incidents.message(id, form.getVersion(), form.getKind(), form.getBody(), form.getResponseTo(), actor);
        return back(id);
      } catch (edu.bits.itsm.shared.BusinessException ex) {
        errors.reject("message", ex.getMessage());
        if (ex instanceof edu.bits.itsm.shared.ConflictException) status = 409;
      }
    }
    response.setStatus(status);
    model.addAttribute("messageError", errors.getAllErrors().getFirst().getDefaultMessage());
    return detail(id, actor, model);
  }

  @PostMapping("/incidents/{id}/feedback")
  String feedback(
      @PathVariable long id,
      @jakarta.validation.Valid @ModelAttribute("feedbackForm") IncidentFeedbackForm form,
      org.springframework.validation.BindingResult errors,
      @AuthenticationPrincipal AccountPrincipal actor,
      Model model,
      jakarta.servlet.http.HttpServletResponse response) {
    if (!errors.hasErrors()) {
      try {
        incidents.feedback(id, form.getRating(), form.getComment(), actor);
        return back(id);
      } catch (edu.bits.itsm.shared.BusinessException ex) {
        errors.reject("feedback", ex.getMessage());
      }
    }
    response.setStatus(400);
    return detail(id, actor, model);
  }

  @PostMapping("/admin/incidents/{id}/assignment")
  String assign(
      @PathVariable long id,
      @RequestParam long version,
      @RequestParam(required = false) Long technician,
      @RequestParam String reason,
      @AuthenticationPrincipal AccountPrincipal actor) {
    incidents.assign(id, version, technician, reason, actor);
    return back(id);
  }

  @PostMapping("/admin/incidents/{id}/correction")
  String correction(
      @PathVariable long id,
      @RequestParam long version,
      @RequestParam long category,
      @RequestParam int impact,
      @RequestParam int urgency,
      @RequestParam String reason,
      @AuthenticationPrincipal AccountPrincipal actor) {
    incidents.correct(id, version, category, impact, urgency, reason, actor);
    return back(id);
  }

  @PostMapping("/incidents/{id}/ai")
  String suggest(
      @PathVariable long id,
      @RequestParam long version,
      @RequestParam String kind,
      @AuthenticationPrincipal AccountPrincipal actor,
      RedirectAttributes flash) {
    ai.suggest(id, version, kind, actor);
    flash.addFlashAttribute("success", "AI suggestion generated. Review it before applying.");
    return back(id);
  }

  @PostMapping("/incidents/{id}/ai/apply")
  String apply(
      @PathVariable long id,
      @RequestParam long version,
      @RequestParam long suggestion,
      @AuthenticationPrincipal AccountPrincipal actor) {
    incidents.applySuggestion(id, version, suggestion, actor);
    return back(id);
  }

  @GetMapping("/notifications")
  String notifications(@AuthenticationPrincipal AccountPrincipal actor, Model model) {
    model.addAttribute("rows", notifications.inbox(actor.getAccountId()));
    return "notifications";
  }

  @PostMapping("/notifications/{id}/read")
  String read(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal actor) {
    notifications.read(id, actor.getAccountId());
    return "redirect:/notifications";
  }

  @GetMapping("/admin/catalog")
  String catalog(Model model, @AuthenticationPrincipal AccountPrincipal actor) {
    catalog(model);
    model.addAttribute("policies", catalog.policies());
    model.addAttribute("strategy", assignment.selected());
    model.addAttribute("skills", assignment.skills());
    return "admin/catalog";
  }

  @PostMapping("/admin/catalog/categories")
  String category(
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam(defaultValue = "false") boolean active,
      @AuthenticationPrincipal AccountPrincipal actor) {
    catalog.saveCategory(id, name, active, actor.getAccountId());
    return "redirect:/admin/catalog";
  }

  @PostMapping("/admin/catalog/services")
  String service(
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam int criticality,
      @RequestParam(defaultValue = "false") boolean active,
      @AuthenticationPrincipal AccountPrincipal actor) {
    catalog.saveService(id, name, criticality, active, actor.getAccountId());
    return "redirect:/admin/catalog";
  }

  @PostMapping("/admin/catalog/sla")
  String policy(
      @RequestParam(required = false) Long service,
      @RequestParam String priority,
      @RequestParam int response,
      @RequestParam int resolution,
      @AuthenticationPrincipal AccountPrincipal actor) {
    catalog.savePolicy(service, priority, response, resolution, actor.getAccountId());
    return "redirect:/admin/catalog";
  }

  @PostMapping("/admin/catalog/strategy")
  String strategy(@RequestParam String strategy, @AuthenticationPrincipal AccountPrincipal actor) {
    assignment.configure(strategy, actor.getAccountId());
    return "redirect:/admin/catalog";
  }

  @PostMapping("/admin/catalog/technician")
  String technician(
      @RequestParam long technician,
      @RequestParam(defaultValue = "false") boolean available,
      @RequestParam int capacity,
      @AuthenticationPrincipal AccountPrincipal actor) {
    assignment.technician(technician, available, capacity, actor.getAccountId());
    return "redirect:/admin/catalog";
  }

  @PostMapping("/admin/catalog/skill")
  String skill(
      @RequestParam long technician,
      @RequestParam long category,
      @RequestParam int proficiency,
      @AuthenticationPrincipal AccountPrincipal actor) {
    assignment.skill(technician, category, proficiency, actor.getAccountId());
    return "redirect:/admin/catalog";
  }

  @GetMapping("/admin/audit")
  String audit(Model model, @AuthenticationPrincipal AccountPrincipal actor) {
    model.addAttribute("rows", history.allAudit(actor));
    return "admin/audit";
  }

  @GetMapping("/admin/email")
  String email(Model model, @AuthenticationPrincipal AccountPrincipal actor) {
    model.addAttribute("rows", history.email(actor));
    return "admin/email";
  }

  private String back(long id) {
    return "redirect:/incidents/" + id;
  }
}
