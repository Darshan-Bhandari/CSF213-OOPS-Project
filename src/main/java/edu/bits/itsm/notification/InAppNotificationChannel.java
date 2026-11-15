package edu.bits.itsm.notification;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationChannel extends BaseNotificationChannel {
  public InAppNotificationChannel(JdbcTemplate jdbc, Clock clock) {
    super(jdbc, clock);
  }

  protected String channel() {
    return "IN_APP";
  }

  protected String deliveryStatus() {
    return "DELIVERED";
  }
}
