package edu.bits.itsm.notification;
import org.springframework.stereotype.Component;import org.springframework.jdbc.core.JdbcTemplate;import java.time.Clock;
@Component public class SimulatedEmailChannel extends BaseNotificationChannel {
 public SimulatedEmailChannel(JdbcTemplate jdbc,Clock clock){super(jdbc,clock);}
 protected String channel(){return "SIMULATED_EMAIL";}protected String deliveryStatus(){return "SIMULATED";}
}
