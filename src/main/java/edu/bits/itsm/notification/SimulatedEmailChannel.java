package edu.bits.itsm.notification;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SimulatedEmailChannel extends BaseNotificationChannel {
  public SimulatedEmailChannel(JdbcTemplate jdbc, Clock clock) {
    super(jdbc, clock);
  }

  protected String channel() {
    return "SIMULATED_EMAIL";
  }

  protected String deliveryStatus() {
    return "SIMULATED";
  }
}
