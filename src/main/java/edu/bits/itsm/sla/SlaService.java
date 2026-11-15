package edu.bits.itsm.sla;
import edu.bits.itsm.incident.Incident;import edu.bits.itsm.notification.NotificationService;import edu.bits.itsm.audit.AuditService;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;import java.sql.Timestamp;
@Service public class SlaService {
 private final JdbcTemplate jdbc;private final NotificationService notifications;private final AuditService audit;private final Clock clock;
 public SlaService(JdbcTemplate jdbc,NotificationService notifications,AuditService audit,Clock clock){this.jdbc=jdbc;this.notifications=notifications;this.audit=audit;this.clock=clock;}
 public void startCycle(Incident i){jdbc.update("INSERT INTO resolution_cycles(incident_id,cycle,started_at,deadline) VALUES (?,?,?,?)",i.getId(),i.getCycle(),Timestamp.from(i.getCycleStartedAt()),Timestamp.from(i.getResolutionDue()));}
 public void updateCycle(Incident i){jdbc.update("UPDATE resolution_cycles SET deadline=?,resolved_at=? WHERE incident_id=? AND cycle=?",Timestamp.from(i.getResolutionDue()),i.getResolvedAt()==null?null:Timestamp.from(i.getResolvedAt()),i.getId(),i.getCycle());}
 public void check(Incident i){
  checkTarget(i,"RESPONSE",1,i.getCreatedAt(),i.getResponseDue(),i.getAcknowledgedAt());
  checkTarget(i,"RESOLUTION",i.getCycle(),i.getCycleStartedAt(),i.getResolutionDue(),i.getResolvedAt());
 }
 private void checkTarget(Incident i,String type,int cycle,Instant start,Instant deadline,Instant completed){
  Instant now=clock.instant();boolean late=completed!=null?completed.isAfter(deadline):now.isAfter(deadline);
  if(late)event(i,type+"_BREACH",cycle,deadline);
  else if(completed==null&&!now.isBefore(start.plusMillis(Duration.between(start,deadline).toMillis()*80/100)))event(i,type+"_WARNING",cycle,deadline);
 }
 private void event(Incident i,String kind,int cycle,Instant deadline){int inserted=jdbc.update("INSERT INTO sla_events(incident_id,cycle,kind,deadline,created_at) VALUES (?,?,?,?,?) ON CONFLICT(incident_id,cycle,kind) DO NOTHING",i.getId(),cycle,kind,Timestamp.from(deadline),Timestamp.from(clock.instant()));if(inserted==1){audit.incident(null,i.getId(),kind,null,null,"Cycle "+cycle+", deadline "+deadline);notifications.incident(i,kind.replace('_',' '),"INC-"+i.getId()+" · deadline "+deadline);}}
}
