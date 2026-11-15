package edu.bits.itsm.incident;

import edu.bits.itsm.identity.UserAccount;
import edu.bits.itsm.shared.BusinessException;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;
  private String title, description;
  @Column(name = "reporter_id")
  private Long reporterId;

  private Long serviceId, categoryId;

  @Column(name = "assignee_id")
  private Long assigneeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reporter_id", insertable = false, updatable = false)
  private UserAccount reporter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assignee_id", insertable = false, updatable = false)
  private UserAccount assignee;

  private int affectedUsers;
  @Embedded private PriorityAssessment assessment;

  @Enumerated(EnumType.STRING)
  private IncidentStatus status;

  private Instant createdAt,
      updatedAt,
      responseDue,
      resolutionDue,
      acknowledgedAt,
      firstResolvedAt,
      resolvedAt,
      closedAt,
      cycleStartedAt;
  private int responseSeconds, resolutionSeconds, cycle;
  private String resolutionDraft = "";

  protected Incident() {}

  public Incident(
      String title,
      String description,
      long reporterId,
      long serviceId,
      long categoryId,
      int affectedUsers,
      PriorityAssessment assessment,
      Instant now,
      int responseSeconds,
      int resolutionSeconds) {
    if (title == null
        || title.isBlank()
        || title.length() > 160
        || description == null
        || description.isBlank()
        || description.length() > 8000
        || affectedUsers < 1)
      throw new BusinessException(
          "Supply a title (up to 160 characters), description (up to 8000), and positive"
              + " affected-user count.");
    this.title = title.strip();
    this.description = description.strip();
    this.reporterId = reporterId;
    this.serviceId = serviceId;
    this.categoryId = categoryId;
    this.affectedUsers = affectedUsers;
    this.assessment = assessment;
    this.status = IncidentStatus.NEW;
    this.createdAt = now;
    this.updatedAt = now;
    this.cycleStartedAt = now;
    this.cycle = 1;
    this.responseSeconds = responseSeconds;
    this.resolutionSeconds = resolutionSeconds;
    responseDue = now.plusSeconds(responseSeconds);
    resolutionDue = now.plusSeconds(resolutionSeconds);
  }

  public void transition(IncidentStatus next, Instant now) {
    IncidentState.forStatus(status).checkTransition(next);
    status = next;
    touch(now);
    if (next == IncidentStatus.ACKNOWLEDGED && acknowledgedAt == null) acknowledgedAt = now;
    if (next == IncidentStatus.RESOLVED) {
      resolvedAt = now;
      if (firstResolvedAt == null) firstResolvedAt = now;
    }
    if (next == IncidentStatus.CLOSED) closedAt = now;
  }

  public void assign(long userId, Instant now) {
    if (status != IncidentStatus.CLASSIFIED)
      throw new BusinessException("Incident must be classified before assignment.");
    assigneeId = userId;
    transition(IncidentStatus.ASSIGNED, now);
  }

  public void unassign(Instant now) {
    if (!status.isActive() || status == IncidentStatus.NEW)
      throw new BusinessException("Only active classified incidents can be reassigned.");
    if (status != IncidentStatus.CLASSIFIED) transition(IncidentStatus.CLASSIFIED, now);
    assigneeId = null;
    touch(now);
  }

  public void reject(Instant now) {
    if (status != IncidentStatus.RESOLVED)
      throw new BusinessException("Only resolved incidents can be rejected.");
    transition(IncidentStatus.CLASSIFIED, now);
    assigneeId = null;
    resolvedAt = null;
    resolutionDraft = "";
    cycle++;
    cycleStartedAt = now;
    resolutionDue = now.plusSeconds(resolutionSeconds);
  }

  public void correct(
      long category, int impact, int urgency, Instant now, int response, int resolution) {
    if (!status.isActive()) throw new BusinessException("Only active incidents can be corrected.");
    assessment = new PriorityAssessment(impact, urgency, assessment.getCriticality());
    categoryId = category;
    responseSeconds = response;
    resolutionSeconds = resolution;
    if (acknowledgedAt == null) responseDue = createdAt.plusSeconds(response);
    resolutionDue = cycleStartedAt.plusSeconds(resolution);
    touch(now);
  }

  public void changeCategory(long category, Instant now) {
    categoryId = category;
    touch(now);
  }

  public void draft(String value, Instant now) {
    resolutionDraft = value;
    touch(now);
  }

  public void touch(Instant now) {
    updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Long getReporterId() {
    return reporterId;
  }

  public Long getServiceId() {
    return serviceId;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public int getAffectedUsers() {
    return affectedUsers;
  }

  public PriorityAssessment getAssessment() {
    return assessment;
  }

  public IncidentStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getResponseDue() {
    return responseDue;
  }

  public Instant getResolutionDue() {
    return resolutionDue;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public Instant getFirstResolvedAt() {
    return firstResolvedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public int getResponseSeconds() {
    return responseSeconds;
  }

  public int getResolutionSeconds() {
    return resolutionSeconds;
  }

  public int getCycle() {
    return cycle;
  }

  public Instant getCycleStartedAt() {
    return cycleStartedAt;
  }

  public String getResolutionDraft() {
    return resolutionDraft;
  }
}
