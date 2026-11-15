package edu.bits.itsm.ai;

import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.catalog.CatalogService;
import edu.bits.itsm.identity.AccountPrincipal;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.shared.ConflictException;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSuggestionStore {
  private final CatalogService catalog;
  private final IncidentService incidents;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final AuditService audit;

  public AiSuggestionStore(
      CatalogService catalog,
      IncidentService incidents,
      JdbcTemplate jdbc,
      Clock clock,
      AuditService audit) {
    this.catalog = catalog;
    this.incidents = incidents;
    this.jdbc = jdbc;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional
  public void save(
      long id,
      long version,
      AccountPrincipal actor,
      String kind,
      Long category,
      String content,
      String model) {
    catalog.lock();
    var i = incidents.view(id, actor);
    incidents.requireStaff(i, actor);
    if (i.getVersion() != version) throw new ConflictException();
    jdbc.update(
        "INSERT INTO"
            + " ai_suggestions(incident_id,incident_version,requested_by,kind,category_id,content,model,created_at)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        id,
        version,
        actor.getAccountId(),
        kind,
        category,
        content,
        model,
        Timestamp.from(clock.instant()));
    audit.incident(
        actor.getAccountId(),
        id,
        "AI_SUGGESTION_GENERATED",
        i.getStatus().name(),
        i.getStatus().name(),
        kind + " suggestion saved for human review; model=" + model);
  }
}
