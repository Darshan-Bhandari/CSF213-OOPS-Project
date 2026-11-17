package edu.bits.itsm.reporting;

import edu.bits.itsm.identity.*;
import edu.bits.itsm.incident.IncidentFilter;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;

@Service
public class IncidentQueryService {
  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  public IncidentQueryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  private Query query(AccountPrincipal actor, IncidentFilter f) {
    if (actor == null || !Boolean.TRUE.equals(jdbc.getJdbcTemplate().queryForObject(
        "SELECT EXISTS(SELECT 1 FROM user_accounts WHERE id=? AND active AND role=?)",
        Boolean.class, actor.getAccountId(), actor.getRole().name())))
      throw new org.springframework.security.access.AccessDeniedException("An active account with the current role is required.");
    StringBuilder w = new StringBuilder(" WHERE 1=1 ");
    var p = new MapSqlParameterSource().addValue("now", Timestamp.from(clock.instant()));
    if (actor.getRole() == Role.EMPLOYEE) {
      w.append(" AND i.reporter_id=:actor");
      p.addValue("actor", actor.getAccountId());
    } else if (actor.getRole() == Role.TECHNICIAN) {
      w.append(" AND i.assignee_id=:actor");
      p.addValue("actor", actor.getAccountId());
    }
    if (!f.getQuery().isBlank()) {
      w.append(
          " AND (lower(i.title) LIKE :q OR lower(i.description) LIKE :q OR ('inc-'||i.id::text)"
              + " LIKE :q OR i.id::text=:id)");
      p.addValue("q", "%" + f.getQuery().strip().toLowerCase(Locale.ROOT) + "%")
          .addValue("id", f.getQuery().strip());
    }
    if (!f.getStatus().isBlank()) {
      w.append(" AND i.status=:status");
      p.addValue("status", f.getStatus());
    }
    if (!f.getPriority().isBlank()) {
      w.append(" AND i.priority=:priority");
      p.addValue("priority", f.getPriority());
    }
    if (f.getCategory() != null) {
      w.append(" AND i.category_id=:category");
      p.addValue("category", f.getCategory());
    }
    if (f.getService() != null) {
      w.append(" AND i.service_id=:service");
      p.addValue("service", f.getService());
    }
    if (f.getTechnician() != null) {
      w.append(" AND i.assignee_id=:technician");
      p.addValue("technician", f.getTechnician());
    }
    if (f.getFrom() != null) {
      w.append(" AND i.created_at>=:from");
      p.addValue(
          "from", Timestamp.from(f.getFrom().atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()));
    }
    if (f.getTo() != null) {
      w.append(" AND i.created_at<:to");
      p.addValue(
          "to",
          Timestamp.from(
              f.getTo().plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()));
    }
    String breach =
        "EXISTS(SELECT 1 FROM sla_events se WHERE se.incident_id=i.id AND se.kind LIKE '%BREACH')";
    if (f.getSla().equals("BREACHED")) w.append(" AND " + breach);
    else if (f.getSla().equals("CLEAR")) w.append(" AND NOT " + breach);
    else if (f.getSla().equals("WARNING"))
      w.append(
          " AND NOT "
              + breach
              + " AND EXISTS(SELECT 1 FROM sla_events se WHERE se.incident_id=i.id AND se.kind LIKE"
              + " '%WARNING')");
    return new Query(w.toString(), p);
  }

  public List<Map<String, Object>> list(AccountPrincipal actor, IncidentFilter f) {
    var q = query(actor, f);
    q.params.addValue("limit", 20).addValue("offset", f.getPage() * 20);
    return jdbc.queryForList(
        "SELECT"
            + " i.id,i.version,i.title,i.status,i.priority,i.score,i.created_at,i.updated_at,i.service_id,i.category_id,i.assignee_id,s.name"
            + " service_name,c.name category_name,u.display_name technician_name,EXISTS(SELECT 1"
            + " FROM sla_events se WHERE se.incident_id=i.id AND se.kind LIKE '%BREACH') breached"
            + " FROM incidents i JOIN it_services s ON s.id=i.service_id JOIN categories c ON"
            + " c.id=i.category_id LEFT JOIN user_accounts u ON u.id=i.assignee_id"
            + q.where
            + " ORDER BY i.created_at DESC,i.id DESC LIMIT :limit OFFSET :offset",
        q.params);
  }

  public long count(AccountPrincipal actor, IncidentFilter f) {
    var q = query(actor, f);
    return jdbc.queryForObject("SELECT count(*) FROM incidents i" + q.where, q.params, Long.class);
  }

  public Map<String, Object> metrics(AccountPrincipal actor, IncidentFilter f) {
    var q = query(actor, f);
    return jdbc.queryForMap(
        "SELECT count(*) total,count(*) FILTER(WHERE status NOT IN"
            + " ('RESOLVED','VERIFIED','CLOSED')) backlog,count(*) FILTER(WHERE priority='P1' AND"
            + " status NOT IN ('RESOLVED','VERIFIED','CLOSED')) critical,count(*) FILTER(WHERE"
            + " status IN ('RESOLVED','VERIFIED')) resolved,count(*) FILTER(WHERE status='CLOSED')"
            + " closed,count(*) FILTER(WHERE EXISTS(SELECT 1 FROM sla_events se WHERE"
            + " se.incident_id=i.id AND se.kind LIKE '%BREACH')) breaches,round(avg(extract(epoch"
            + " from (acknowledged_at-created_at)))/60,1) avg_response, count(acknowledged_at)"
            + " response_samples,round(avg(extract(epoch from(first_resolved_at-created_at)))/60,1)"
            + " avg_resolution,count(first_resolved_at) resolution_samples,count(*) FILTER(WHERE"
            + " first_resolved_at IS NOT NULL OR response_due<:now OR EXISTS(SELECT 1 FROM"
            + " resolution_cycles rc WHERE rc.incident_id=i.id AND rc.cycle=1 AND"
            + " rc.deadline<:now)) sla_eligible,count(*) FILTER(WHERE (first_resolved_at IS NOT"
            + " NULL OR response_due<:now OR EXISTS(SELECT 1 FROM resolution_cycles rc WHERE"
            + " rc.incident_id=i.id AND rc.cycle=1 AND rc.deadline<:now)) AND NOT EXISTS(SELECT 1"
            + " FROM sla_events se WHERE se.incident_id=i.id AND se.cycle=1 AND se.kind LIKE"
            + " '%BREACH')) sla_met FROM incidents i"
            + q.where,
        q.params);
  }

  public List<Map<String, Object>> byCategory(AccountPrincipal actor, IncidentFilter f) {
    var q = query(actor, f);
    return jdbc.queryForList(
        "SELECT c.name,count(*) total FROM incidents i JOIN categories c ON c.id=i.category_id"
            + q.where
            + " GROUP BY c.name ORDER BY total DESC",
        q.params);
  }

  private record Query(String where, MapSqlParameterSource params) {}
}
