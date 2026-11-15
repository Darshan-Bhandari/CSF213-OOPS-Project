package edu.bits.itsm.incident;

import edu.bits.itsm.assignment.AssignmentService;
import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.notification.NotificationService;
import edu.bits.itsm.shared.*;
import edu.bits.itsm.sla.SlaService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
  private final IncidentRepository incidents;
  private final CatalogService catalog;
  private final AssignmentService assignment;
  private final AuditService audit;
  private final NotificationService notifications;
  private final SlaService sla;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public IncidentService(
      IncidentRepository incidents,
      CatalogService catalog,
      AssignmentService assignment,
      AuditService audit,
      NotificationService notifications,
      SlaService sla,
      JdbcTemplate jdbc,
      Clock clock) {
    this.incidents = incidents;
    this.catalog = catalog;
    this.assignment = assignment;
    this.audit = audit;
    this.notifications = notifications;
    this.sla = sla;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Incident view(long id, AccountPrincipal actor) {
    var i = incidents.findById(id).orElseThrow(() -> new BusinessException("Incident not found."));
    requireView(i, actor);
    return i;
  }

  public void requireView(Incident i, AccountPrincipal actor) {
    requireCurrentAccount(actor);
    boolean allowed =
        actor.getRole() == Role.ADMIN
            || (actor.getRole() == Role.EMPLOYEE && i.getReporterId() == actor.getAccountId())
            || (actor.getRole() == Role.TECHNICIAN
                && Objects.equals(i.getAssigneeId(), actor.getAccountId()));
    if (!allowed) throw new AccessDeniedException("This incident is not in your workspace.");
  }

  public void requireStaff(Incident i, AccountPrincipal actor) {
    requireView(i, actor);
    if (actor.getRole() == Role.EMPLOYEE)
      throw new AccessDeniedException("Technician access required.");
  }

  private void requireCurrentAccount(AccountPrincipal actor) {
    if (actor == null || !Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM user_accounts WHERE id=? AND active AND role=?)",
        Boolean.class, actor.getAccountId(), actor.getRole().name())))
      throw new AccessDeniedException("An active account with the current role is required.");
  }

  private void admin(AccountPrincipal actor) {
    requireCurrentAccount(actor);
    if (actor.getRole() != Role.ADMIN)
      throw new AccessDeniedException("Administrator access required.");
  }

  private Incident editing(long id, long version, AccountPrincipal actor) {
    catalog.lock();
    var i = view(id, actor);
    if (i.getVersion() != version) throw new ConflictException();
    return i;
  }

  private void technician(Incident i, AccountPrincipal actor) {
    if (actor.getRole() != Role.TECHNICIAN
        || !Objects.equals(i.getAssigneeId(), actor.getAccountId()))
      throw new AccessDeniedException("Only the assigned technician can perform this action.");
  }

  private String text(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max)
      throw new BusinessException("Supply text between 1 and " + max + " characters.");
    return value.strip();
  }

  @Transactional
  public Incident create(
      AccountPrincipal actor,
      String title,
      String description,
      long service,
      long category,
      int impact,
      int urgency,
      int affected) {
    requireCurrentAccount(actor);
    if (actor.getRole() != Role.EMPLOYEE)
      throw new AccessDeniedException("Employees report incidents.");
    catalog.lock();
    var configured = catalog.service(service);
    catalog.requireCategory(category);
    var score =
        new PriorityAssessment(
            impact, urgency, ((Number) configured.get("criticality")).intValue());
    var policy = catalog.policy(service, score.getPriority());
    var i =
        incidents.saveAndFlush(
            new Incident(
                title,
                description,
                actor.getAccountId(),
                service,
                category,
                affected,
                score,
                clock.instant(),
                policy.responseSeconds(),
                policy.resolutionSeconds()));
    audit.incident(actor.getAccountId(), i.getId(), "CREATED", null, "NEW", "Incident reported");
    step(
        i,
        IncidentStatus.CLASSIFIED,
        actor.getAccountId(),
        "CLASSIFIED",
        "Category "
            + category
            + "; priority "
            + score.getPriority()
            + "; score "
            + score.getScore()
            + "; impact=" + score.getImpact() + "; urgency=" + score.getUrgency()
            + "; criticality snapshot=" + score.getCriticality());
    sla.startCycle(i);
    autoAssign(i, actor.getAccountId());
    notifications.incident(i, "Incident created", "INC-" + i.getId() + ": " + i.getTitle());
    return incidents.saveAndFlush(i);
  }

  private void step(Incident i, IncidentStatus target, Long actor, String action, String details) {
    String before = i.getStatus().name();
    i.transition(target, clock.instant());
    audit.incident(actor, i.getId(), action, before, target.name(), details);
  }

  private void autoAssign(Incident i, Long actor) {
    var chosen = assignment.choose(i.getCategoryId());
    if (chosen.isPresent())
      assign(i, chosen.get().userId(), actor, "Automatic " + assignment.selected() + " assignment");
    else {
      audit.incident(
          actor,
          i.getId(),
          "ASSIGNMENT_PENDING",
          i.getStatus().name(),
          i.getStatus().name(),
          "No available eligible technician");
      notifications.incident(
          i, "Assignment required", "INC-" + i.getId() + " has no eligible technician.");
    }
  }

  private void assign(Incident i, long technician, Long actor, String reason) {
    assignment.requireEligible(technician, i.getCategoryId());
    String old = i.getStatus().name();
    i.assign(technician, clock.instant());
    audit.incident(
        actor, i.getId(), "ASSIGNED", old, "ASSIGNED", "Technician " + technician + "; " + reason);
    notifications.incident(
        i, "Incident assigned", "INC-" + i.getId() + " assigned to technician " + technician);
  }

  @Transactional
  public void assign(
      long id, long version, Long technician, String reason, AccountPrincipal actor) {
    admin(actor);
    var i = editing(id, version, actor);
    reason = text(reason, 1000);
    Long old = i.getAssigneeId();
    String previousState = i.getStatus().name();
    i.unassign(clock.instant());
    audit.incident(
        actor.getAccountId(),
        id,
        "RECLASSIFIED_FOR_ASSIGNMENT",
        previousState,
        "CLASSIFIED",
        reason);
    incidents.saveAndFlush(i);
    if (technician == null) autoAssign(i, actor.getAccountId());
    else assign(i, technician, actor.getAccountId(), reason);
    audit.incident(
        actor.getAccountId(),
        id,
        "REASSIGNMENT",
        i.getStatus().name(),
        i.getStatus().name(),
        "Previous technician=" + old + "; " + reason);
  }

  @Transactional
  public void transition(
      long id, long version, String action, String details, AccountPrincipal actor) {
    var i = editing(id, version, actor);
    Instant now = clock.instant();
    if (action == null) throw new BusinessException("Choose an incident action.");
    switch (action) {
      case "ACKNOWLEDGE" -> {
        technician(i, actor);
        if (i.getStatus() != IncidentStatus.ASSIGNED)
          throw new BusinessException("Only assigned incidents can be acknowledged.");
        step(
            i,
            IncidentStatus.ACKNOWLEDGED,
            actor.getAccountId(),
            "ACKNOWLEDGED",
            "Technician acknowledged assignment");
      }
      case "START" -> {
        technician(i, actor);
        step(
            i,
            IncidentStatus.IN_PROGRESS,
            actor.getAccountId(),
            "INVESTIGATION_STARTED",
            "Investigation started");
      }
      case "RESOLVE" -> {
        technician(i, actor);
        details = text(details, 8000);
        step(i, IncidentStatus.RESOLVED, actor.getAccountId(), "RESOLVED", "Resolution recorded for cycle " + i.getCycle());
        jdbc.update(
            "INSERT INTO resolutions(incident_id,cycle,technician_id,details,created_at) VALUES"
                + " (?,?,?,?,?)",
            id,
            i.getCycle(),
            actor.getAccountId(),
            details,
            Timestamp.from(now));
        sla.updateCycle(i);
        notifications.incident(
            i, "Incident resolved", "INC-" + id + " is ready for reporter verification.");
      }
      case "VERIFY", "CLOSE", "VERIFY_CLOSE" -> {
        if (actor.getRole() != Role.EMPLOYEE && actor.getRole() != Role.ADMIN)
          throw new AccessDeniedException("Reporter verification required.");
        String why =
            actor.getRole() == Role.ADMIN ? "Administrator override: " + text(details, 1000) : "Reporter confirmed resolution";
        if (!action.equals("CLOSE"))
          step(i, IncidentStatus.VERIFIED, actor.getAccountId(), "VERIFIED", why);
        if (!action.equals("VERIFY")) {
          step(i, IncidentStatus.CLOSED, actor.getAccountId(), "CLOSED", why);
          notifications.incident(i, "Incident closed", "INC-" + id + " closed after verification.");
        }
      }
      case "REJECT" -> {
        if (actor.getRole() != Role.EMPLOYEE)
          throw new AccessDeniedException("Only the reporter can reject a resolution.");
        details = text(details, 1000);
        sla.check(i);
        i.reject(now);
        audit.incident(
            actor.getAccountId(), id, "RESOLUTION_REJECTED", "RESOLVED", "CLASSIFIED",
            "Cycle " + (i.getCycle() - 1) + " rejected: " + details + "; started cycle " + i.getCycle());
        sla.startCycle(i);
        incidents.saveAndFlush(i);
        autoAssign(i, actor.getAccountId());
      }
      default -> throw new BusinessException("Unknown workflow action.");
    }
    incidents.saveAndFlush(i);
    sla.check(i);
  }

  @Transactional
  public void message(
      long id, long version, String kind, String body, Long responseTo, AccountPrincipal actor) {
    var i = editing(id, version, actor);
    body = text(body, 4000);
    if (!i.getStatus().isActive())
      throw new BusinessException("Add investigation messages while the incident is active.");
    if (kind == null || !Set.of("PUBLIC", "INTERNAL", "REQUEST").contains(kind))
      throw new BusinessException("Unknown message type.");
    if (!kind.equals("PUBLIC")) technician(i, actor);
    if (responseTo != null) {
      if (actor.getRole() != Role.EMPLOYEE || !kind.equals("PUBLIC"))
        throw new BusinessException("Only the reporter can send public replies to information requests.");
      if (jdbc.queryForObject(
              "SELECT count(*) FROM incident_messages WHERE id=? AND incident_id=? AND kind='REQUEST'",
              Long.class, responseTo, id) != 1)
        throw new BusinessException("Information request not found on this incident.");
    }
    Long messageId = jdbc.queryForObject(
        "INSERT INTO incident_messages(incident_id,author_id,kind,body,response_to,created_at)"
            + " VALUES (?,?,?,?,?,?) RETURNING id",
        Long.class, id, actor.getAccountId(), kind, body, responseTo, Timestamp.from(clock.instant()));
    i.recordMessage(clock.instant());
    audit.incident(actor.getAccountId(), id, "MESSAGE_ADDED", i.getStatus().name(),
        i.getStatus().name(), kind + " message #" + messageId
            + (responseTo == null ? "" : "; reply to request #" + responseTo));
    if (!kind.equals("INTERNAL"))
      notifications.incident(i,
          kind.equals("REQUEST") ? "Information requested" : "New incident reply",
          "INC-" + id + " has a new message.");
    incidents.saveAndFlush(i);
  }

  @Transactional
  public void feedback(long id, int rating, String comment, AccountPrincipal actor) {
    catalog.lock();
    var i = view(id, actor);
    if (actor.getRole() != Role.EMPLOYEE)
      throw new AccessDeniedException("Only the reporter can submit feedback.");
    if (i.getStatus().isActive())
      throw new BusinessException("The reporter can rate an incident after resolution.");
    if (rating < 1 || rating > 5 || comment == null || comment.length() > 2000)
      throw new BusinessException("Rating must be 1–5; comment at most 2000 characters.");
    if (jdbc.queryForObject("SELECT count(*) FROM feedback WHERE incident_id=?", Long.class, id)
        > 0) throw new BusinessException("Feedback already submitted.");
    jdbc.update(
        "INSERT INTO feedback VALUES (?,?,?,?,?)",
        id,
        actor.getAccountId(),
        rating,
        comment.strip(),
        Timestamp.from(clock.instant()));
    audit.incident(actor.getAccountId(), id, "FEEDBACK_ADDED", i.getStatus().name(), i.getStatus().name(), "Rating " + rating);
  }

  @Transactional
  public void correct(
      long id,
      long version,
      long category,
      int impact,
      int urgency,
      String reason,
      AccountPrincipal actor) {
    admin(actor);
    var i = editing(id, version, actor);
    reason = text(reason, 1000);
    catalog.requireCategory(category);
    sla.check(i);
    var oldAssessment = i.getAssessment();
    long oldCategory = i.getCategoryId();
    var oldPriority = oldAssessment.getPriority();
    var score = new PriorityAssessment(impact, urgency, i.getAssessment().getCriticality());
    var policy = catalog.policy(i.getServiceId(), score.getPriority());
    i.correct(
        category,
        impact,
        urgency,
        clock.instant(),
        policy.responseSeconds(),
        policy.resolutionSeconds());
    sla.updateCycle(i);
    audit.incident(
        actor.getAccountId(),
        id,
        "INCIDENT_CORRECTED",
        i.getStatus().name(),
        i.getStatus().name(),
        "Priority=" + oldPriority + " -> " + score.getPriority()
            + "; category=" + oldCategory + " -> " + category
            + "; impact=" + oldAssessment.getImpact() + " -> " + impact
            + "; urgency=" + oldAssessment.getUrgency() + " -> " + urgency
            + "; criticality snapshot=" + score.getCriticality()
            + "; score=" + oldAssessment.getScore() + " -> " + score.getScore()
            + "; reason=" + reason);
    if (i.getAssigneeId() != null && !assignment.hasSkill(i.getAssigneeId(), category)) {
      String beforeReassignment = i.getStatus().name();
      i.unassign(clock.instant());
      audit.incident(
          actor.getAccountId(),
          id,
          "CATEGORY_REASSIGNMENT_REQUIRED",
          beforeReassignment,
          "CLASSIFIED",
          "Previous assignee lacks corrected category skill");
      incidents.saveAndFlush(i);
      autoAssign(i, actor.getAccountId());
    }
    incidents.saveAndFlush(i);
    sla.check(i);
    notifications.incident(
        i, "Incident priority/category updated", "INC-" + id + " priority " + score.getPriority());
  }

  @Transactional
  public void applySuggestion(long id, long version, long suggestion, AccountPrincipal actor) {
    var i = editing(id, version, actor);
    requireStaff(i, actor);
    var rows =
        jdbc.queryForList(
            "SELECT * FROM ai_suggestions WHERE id=? AND incident_id=? AND applied_at IS NULL",
            suggestion,
            id);
    if (rows.isEmpty()) throw new BusinessException("Suggestion not found or already applied.");
    var row = rows.getFirst();
    if (((Number) row.get("incident_version")).longValue() != i.getVersion())
      throw new ConflictException();
    if (row.get("kind").equals("CATEGORY")) {
      long category = ((Number) row.get("category_id")).longValue();
      catalog.requireCategory(category);
      if (i.getAssigneeId() != null && !assignment.hasSkill(i.getAssigneeId(), category))
        throw new BusinessException(
            "Use administrator correction/reassignment because this category requires a different"
                + " technician skill.");
      i.changeCategory(category, clock.instant());
    } else i.draft(row.get("content").toString(), clock.instant());
    if (!i.getStatus().isActive())
      throw new BusinessException("Suggestions can only be applied to active incidents.");
    jdbc.update(
        "UPDATE ai_suggestions SET applied_at=? WHERE id=?",
        Timestamp.from(clock.instant()),
        suggestion);
    audit.incident(
        actor.getAccountId(),
        id,
        "AI_SUGGESTION_APPLIED",
        i.getStatus().name(),
        i.getStatus().name(),
        "Suggestion " + suggestion + "; human confirmed; no lifecycle action");
  }
}
