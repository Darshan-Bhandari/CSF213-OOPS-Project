package edu.bits.itsm.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class WorkflowDemoData {
  private final JdbcTemplate jdbc;
  private final Environment environment;

  public WorkflowDemoData(JdbcTemplate jdbc, Environment environment) {
    this.jdbc = jdbc;
    this.environment = environment;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void seed() {
    if (jdbc.update(
            "INSERT INTO demo_seed_markers(name) VALUES ('initial-technician-skills') ON CONFLICT"
                + " DO NOTHING")
        == 1)
      jdbc.update(
          "INSERT INTO technician_skills(user_id,category_id,proficiency) SELECT u.id,c.id,2 FROM"
              + " user_accounts u CROSS JOIN categories c WHERE u.email='technician@campus.test' ON"
              + " CONFLICT DO NOTHING");
    if (environment.matchesProfiles("sla-demo")
        && jdbc.update(
                "INSERT INTO demo_seed_markers(name) VALUES ('sla-training-service') ON CONFLICT DO"
                    + " NOTHING")
            == 1) {
      jdbc.update(
          "INSERT INTO it_services(name,criticality) VALUES ('Demo SLA training — short targets',3)"
              + " ON CONFLICT(name) DO NOTHING");
      jdbc.update(
          "INSERT INTO sla_policies(service_id,priority,response_seconds,resolution_seconds) SELECT"
              + " id,'P1',10,30 FROM it_services WHERE name='Demo SLA training — short targets' ON"
              + " CONFLICT DO NOTHING");
    }
  }
}
