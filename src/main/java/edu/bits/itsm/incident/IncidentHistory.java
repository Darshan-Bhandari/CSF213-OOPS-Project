package edu.bits.itsm.incident;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IncidentHistory {
  private final JdbcTemplate jdbc;
  private final IncidentService incidents;

  public IncidentHistory(JdbcTemplate jdbc, IncidentService incidents) {
    this.jdbc = jdbc;
    this.incidents = incidents;
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public List<Map<String, Object>> messages(long id, edu.bits.itsm.identity.AccountPrincipal actor) {
    incidents.view(id, actor);
    boolean internal = actor.getRole() != edu.bits.itsm.identity.Role.EMPLOYEE;
    return jdbc.queryForList(
        "SELECT m.*,u.display_name,(SELECT count(*) FROM incident_messages reply WHERE"
            + " reply.response_to=m.id AND reply.kind='PUBLIC' AND reply.incident_id=m.incident_id) reply_count"
            + " FROM incident_messages m JOIN user_accounts u ON u.id=m.author_id"
            + " WHERE m.incident_id=? AND (? OR m.kind<>'INTERNAL') ORDER BY m.id",
        id, internal);
  }

  public List<Map<String, Object>> audit(long id) {
    return jdbc.queryForList(
        "SELECT a.*,COALESCE(u.display_name,'System') actor_name FROM audit_entries a LEFT JOIN"
            + " user_accounts u ON u.id=a.actor_id WHERE incident_id=? ORDER BY a.id",
        id);
  }

  public List<Map<String, Object>> resolutions(long id) {
    return jdbc.queryForList(
        "SELECT r.*,u.display_name FROM resolutions r JOIN user_accounts u ON u.id=r.technician_id"
            + " WHERE incident_id=? ORDER BY r.id DESC",
        id);
  }

  public List<Map<String, Object>> events(long id) {
    return jdbc.queryForList("SELECT * FROM sla_events WHERE incident_id=? ORDER BY id", id);
  }

  public List<Map<String, Object>> suggestions(long id) {
    return jdbc.queryForList(
        "SELECT * FROM ai_suggestions WHERE incident_id=? ORDER BY id DESC LIMIT 10", id);
  }

  public List<Map<String, Object>> feedback(long id) {
    return jdbc.queryForList("SELECT * FROM feedback WHERE incident_id=?", id);
  }

  public List<Map<String, Object>> allAudit() {
    return jdbc.queryForList(
        "SELECT a.*,COALESCE(u.display_name,'System') actor_name FROM audit_entries a LEFT JOIN"
            + " user_accounts u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 300");
  }

  public List<Map<String, Object>> email() {
    return jdbc.queryForList(
        "SELECT n.*,u.email FROM notifications n JOIN user_accounts u ON u.id=n.user_id WHERE"
            + " channel='SIMULATED_EMAIL' ORDER BY n.id DESC LIMIT 200");
  }
}
