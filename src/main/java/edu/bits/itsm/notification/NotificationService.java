package edu.bits.itsm.notification;
import edu.bits.itsm.incident.Incident;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import java.util.*;import java.time.Clock;
@Service public class NotificationService {
 private final List<NotificationChannel> channels;private final JdbcTemplate jdbc;private final Clock clock;
 public NotificationService(List<NotificationChannel> channels,JdbcTemplate jdbc,Clock clock){this.channels=channels;this.jdbc=jdbc;this.clock=clock;}
 @Transactional(propagation=Propagation.MANDATORY) public void incident(Incident incident,String subject,String body){
  Set<Long> users=new HashSet<>(jdbc.queryForList("SELECT id FROM user_accounts WHERE role='ADMIN' AND active",Long.class));users.add(incident.getReporterId());if(incident.getAssigneeId()!=null)users.add(incident.getAssigneeId());
  for(long user:users)for(var channel:channels)channel.deliver(user,incident.getId(),subject,body);
 }
 public List<Map<String,Object>> inbox(long user){return jdbc.queryForList("SELECT * FROM notifications WHERE user_id=? AND channel='IN_APP' ORDER BY id DESC LIMIT 200",user);}
 @Transactional public void read(long id,long user){jdbc.update("UPDATE notifications SET read_at=? WHERE id=? AND user_id=? AND channel='IN_APP'",java.sql.Timestamp.from(clock.instant()),id,user);}
}
