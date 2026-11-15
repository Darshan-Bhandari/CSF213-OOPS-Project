package edu.bits.itsm.notification;
import org.springframework.jdbc.core.JdbcTemplate;import java.time.Clock;
/** Shared persistence behavior for two concrete delivery mechanisms. */
public abstract class BaseNotificationChannel implements NotificationChannel {
 protected final JdbcTemplate jdbc;protected final Clock clock;
 protected BaseNotificationChannel(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 protected abstract String channel();protected abstract String deliveryStatus();
 public final void deliver(long user,long incident,String subject,String body){jdbc.update("INSERT INTO notifications(user_id,incident_id,channel,subject,body,delivery_status,created_at) VALUES (?,?,?,?,?,?,?)",user,incident,channel(),subject,body,deliveryStatus(),java.sql.Timestamp.from(clock.instant()));}
}
