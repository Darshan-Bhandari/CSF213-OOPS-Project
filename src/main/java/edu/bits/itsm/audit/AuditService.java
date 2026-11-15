package edu.bits.itsm.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;

  public AuditService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(Long actor, String action, String subject, String description) {
    jdbc.update(
        "INSERT INTO audit_entries(actor_id,action,subject,description) VALUES (?,?,?,?)",
        actor,
        action,
        subject,
        description);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void incident(Long actor, long incident, String action, String previous, String next, String description) {
    jdbc.update("INSERT INTO audit_entries(actor_id,action,subject,description,incident_id,previous_state,new_state) VALUES (?,?,?,?,?,?,?)",
      actor, action, "INC-"+incident, description, incident, previous, next);
  }
}
