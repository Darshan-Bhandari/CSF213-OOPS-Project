package edu.bits.itsm.incident;

import java.util.*;
import edu.bits.itsm.identity.AccountPrincipal;
import edu.bits.itsm.identity.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Transactional(readOnly = true)
public class IncidentHistory {
  private final JdbcTemplate jdbc;
  private final IncidentService incidents;

  public IncidentHistory(JdbcTemplate jdbc, IncidentService incidents) {
    this.jdbc = jdbc;
    this.incidents = incidents;
  }

  public List<Map<String, Object>> messages(long id, AccountPrincipal actor) {
    incidents.view(id, actor);
    boolean internal = actor.getRole() != Role.EMPLOYEE;
    return jdbc.queryForList(
        "SELECT m.*,u.display_name,(SELECT count(*) FROM incident_messages reply WHERE"
            + " reply.response_to=m.id AND reply.kind='PUBLIC' AND reply.incident_id=m.incident_id) reply_count"
            + " FROM incident_messages m JOIN user_accounts u ON u.id=m.author_id"
            + " WHERE m.incident_id=? AND (? OR m.kind<>'INTERNAL') ORDER BY m.id",
        id, internal);
  }

  public List<Map<String, Object>> audit(long id, AccountPrincipal actor) {
    incidents.view(id, actor);
    return jdbc.queryForList(
        "SELECT a.id,a.actor_id,a.incident_id,a.created_at,a.action,a.subject,a.previous_state,a.new_state,"
            + " CASE WHEN NOT ? AND a.action LIKE 'CORRELATION_%' THEN 'Incident correlation updated' ELSE a.description END description,"
            + " COALESCE(u.display_name,'System') actor_name FROM audit_entries a LEFT JOIN"
            + " user_accounts u ON u.id=a.actor_id WHERE incident_id=?"
            + " AND (? OR (a.action<>'AI_SUGGESTION_GENERATED' AND a.action NOT LIKE 'CORRELATION_%' AND NOT"
            + " (a.action='MESSAGE_ADDED' AND a.description LIKE 'INTERNAL message%'))) ORDER BY a.id",
        actor.getRole() == Role.ADMIN, id, actor.getRole() != Role.EMPLOYEE);
  }

  public List<Map<String, Object>> resolutions(long id, AccountPrincipal actor) {
    incidents.view(id, actor);
    return jdbc.queryForList(
        "SELECT r.*,u.display_name FROM resolutions r JOIN user_accounts u ON u.id=r.technician_id"
            + " WHERE incident_id=? ORDER BY r.id DESC",
        id);
  }

  public List<Map<String, Object>> events(long id, AccountPrincipal actor) {
    incidents.view(id, actor);
    return jdbc.queryForList("SELECT * FROM sla_events WHERE incident_id=? ORDER BY id", id);
  }

  public List<Map<String, Object>> suggestions(long id, AccountPrincipal actor) {
    incidents.requireStaff(incidents.view(id, actor), actor);
    return jdbc.queryForList(
        "SELECT * FROM ai_suggestions WHERE incident_id=? ORDER BY id DESC LIMIT 10", id);
  }

  public List<Map<String, Object>> feedback(long id, AccountPrincipal actor) {
    incidents.view(id, actor);
    return jdbc.queryForList("SELECT * FROM feedback WHERE incident_id=?", id);
  }

  public List<Map<String, Object>> allAudit(AccountPrincipal actor) {
    requireAdmin(actor);
    return jdbc.queryForList(
        "SELECT a.*,COALESCE(u.display_name,'System') actor_name FROM audit_entries a LEFT JOIN"
            + " user_accounts u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 300");
  }

  public List<Map<String, Object>> email(AccountPrincipal actor) {
    requireAdmin(actor);
    return jdbc.queryForList(
        "SELECT n.*,u.email FROM notifications n JOIN user_accounts u ON u.id=n.user_id WHERE"
            + " channel='SIMULATED_EMAIL' ORDER BY n.id DESC LIMIT 200");
  }
  private void requireAdmin(AccountPrincipal actor) {
    if (actor == null || actor.getRole() != Role.ADMIN || !Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM user_accounts WHERE id=? AND active AND role='ADMIN')",
        Boolean.class, actor.getAccountId())))
      throw new AccessDeniedException("Administrator access required.");
  }

}
