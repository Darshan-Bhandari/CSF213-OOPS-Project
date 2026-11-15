package edu.bits.itsm.notification;
import org.springframework.stereotype.Component;import org.springframework.jdbc.core.JdbcTemplate;import java.time.Clock;
@Component public class InAppNotificationChannel extends BaseNotificationChannel {
 public InAppNotificationChannel(JdbcTemplate jdbc,Clock clock){super(jdbc,clock);}
 protected String channel(){return "IN_APP";}protected String deliveryStatus(){return "DELIVERED";}
}
