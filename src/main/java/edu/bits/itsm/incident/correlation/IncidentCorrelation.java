package edu.bits.itsm.incident.correlation;

import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.identity.AccountPrincipal;
import edu.bits.itsm.identity.Role;
import edu.bits.itsm.incident.Incident;
import edu.bits.itsm.shared.BusinessException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Correlation persistence. IncidentService owns command authorization and the workflow lock. */
@Service
public class IncidentCorrelation {
  private static final Duration WINDOW = Duration.ofHours(4);
  private static final int MINIMUM_SCORE = 30;
  private static final int MAX_CANDIDATES = 5;
  private static final String ACTIVE = "i.status NOT IN ('RESOLVED','VERIFIED','CLOSED')";
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final Clock clock;

  public IncidentCorrelation(JdbcTemplate jdbc, AuditService audit, Clock clock) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCandidates(Incident incident) {
    var created = incident.getCreatedAt();
    var terms = IncidentSimilarity.terms(incident.getTitle(), incident.getDescription());
    var matches = jdbc.query(
        """
        SELECT i.id, i.title, i.description
        FROM incidents i
        WHERE i.service_id=? AND i.category_id=? AND i.reporter_id<>?
          AND i.created_at BETWEEN ? AND ? AND i.id<>? AND
        """ + " " + ACTIVE + " ORDER BY i.created_at DESC, i.id DESC LIMIT 200",
        (row, n) -> new Match(row.getLong("id"), IncidentSimilarity.score(terms,
            IncidentSimilarity.terms(row.getString("title"), row.getString("description")))),
        incident.getServiceId(), incident.getCategoryId(), incident.getReporterId(),
        Timestamp.from(created.minus(WINDOW)), Timestamp.from(created), incident.getId())
        .stream().filter(m -> m.similarity() >= MINIMUM_SCORE)
        .sorted(Comparator.comparingInt(Match::similarity).reversed()).limit(MAX_CANDIDATES).toList();
    if (matches.isEmpty()) return;

    jdbc.batchUpdate(
        "INSERT INTO incident_correlation_candidates(incident_id,candidate_id,similarity) VALUES (?,?,?)",
        matches.stream().map(m -> new Object[]{incident.getId(), m.id(), m.similarity()}).toList());
    auditIncident(null, incident, "CORRELATION_CANDIDATES_FOUND",
        "Potential duplicates suggested using recent reports for the same service and category.");
  }

  @Transactional(readOnly = true)
  public Panel panel(long incident, AccountPrincipal staff) {
    boolean administrator = staff.getRole() == Role.ADMIN;
    Long master = masterOf(incident);
    long root = master == null ? incident : master;
    var linked = jdbc.query("""
        SELECT i.id, i.title, i.description, i.status, i.id=? AS master
        FROM incidents i
        WHERE i.id<>? AND (? OR i.assignee_id=?)
          AND (i.id=? OR EXISTS (
            SELECT 1 FROM incident_duplicate_links link WHERE link.incident_id=i.id AND link.master_id=?
          ))
        ORDER BY master DESC, i.id
        """, (r, n) -> new Related(r.getLong("id"), r.getString("title"), r.getString("description"),
            r.getString("status"), r.getBoolean("master")),
        root, incident, administrator, staff.getAccountId(), root, root);

    boolean canLink = master == null && !hasChildren(incident);
    var candidates = jdbc.query("""
        SELECT i.id, i.title, i.description, i.status, c.similarity,
               NOT EXISTS (SELECT 1 FROM incident_duplicate_links link WHERE link.incident_id=i.id)
                   AND i.status NOT IN ('RESOLVED','VERIFIED','CLOSED') AS eligible_master
        FROM incident_correlation_candidates c
        JOIN incidents i ON i.id=c.candidate_id
        WHERE c.incident_id=? AND (? OR i.assignee_id=?)
        ORDER BY c.similarity DESC, i.created_at DESC, i.id DESC
        """, (r, n) -> new Candidate(r.getLong("id"), r.getString("title"), r.getString("description"),
            r.getString("status"), r.getInt("similarity"), r.getBoolean("eligible_master")),
        incident, administrator, staff.getAccountId());
    return new Panel(canLink, master != null, linked, candidates);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean link(Incident report, Incident master, long actor) {
    if (report.getId().equals(master.getId()))
      throw new BusinessException("Choose a different incident as the master.");
    Long current = masterOf(report.getId());
    if (master.getId().equals(current)) return false;
    if (current != null)
      throw new BusinessException("This report is already linked to a master incident.");
    if (!report.getStatus().isActive() || !master.getStatus().isActive())
      throw new BusinessException("Both incidents must be active to create a duplicate link.");
    if (masterOf(master.getId()) != null || hasChildren(report.getId()))
      throw new BusinessException("Choose an unlinked master and a report that has no duplicates of its own.");

    jdbc.update("""
        INSERT INTO incident_duplicate_links(incident_id,master_id,linked_by,linked_at)
        VALUES (?,?,?,?)
        """, report.getId(), master.getId(), actor, Timestamp.from(clock.instant()));
    auditIncident(actor, report, "CORRELATION_DUPLICATE_LINKED", "Linked to master INC-" + master.getId());
    auditIncident(actor, master, "CORRELATION_DUPLICATE_ADDED", "Linked report INC-" + report.getId());
    return true;
  }

  private Long masterOf(long incident) {
    return jdbc.query("SELECT master_id FROM incident_duplicate_links WHERE incident_id=?",
        rows -> rows.next() ? rows.getLong(1) : null, incident);
  }

  private boolean hasChildren(long incident) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM incident_duplicate_links WHERE master_id=?)", Boolean.class, incident));
  }

  private void auditIncident(Long actor, Incident incident, String action, String description) {
    var status = incident.getStatus().name();
    audit.incident(actor, incident.getId(), action, status, status, description);
  }

  private record Match(long id, int similarity) {}
  public record Candidate(long id, String title, String description, String status, int similarity,
                          boolean eligibleMaster) {}
  public record Related(long id, String title, String description, String status, boolean master) {}
  public record Panel(boolean canLink, boolean duplicate, List<Related> linked, List<Candidate> candidates) {}
}
