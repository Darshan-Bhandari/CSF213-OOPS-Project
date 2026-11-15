package edu.bits.itsm.incident;
import edu.bits.itsm.shared.BusinessException;
import java.util.*;
/** State objects own the allowed next steps; controllers cannot bypass them. */
public interface IncidentState {
 void checkTransition(IncidentStatus next);
 static IncidentState forStatus(IncidentStatus status){return States.VALUES.get(status);}
 final class States {
  private static final Map<IncidentStatus,IncidentState> VALUES=new EnumMap<>(IncidentStatus.class);
  static {var states=IncidentStatus.values();for(int i=0;i<states.length;i++){
   IncidentStatus current=states[i];Set<IncidentStatus> next=new HashSet<>();
   if(i+1<states.length)next.add(states[i+1]);
   if(current.isActive()&&current!=IncidentStatus.NEW)next.add(IncidentStatus.CLASSIFIED);
   if(current==IncidentStatus.RESOLVED)next.add(IncidentStatus.CLASSIFIED);
   VALUES.put(current,new WorkflowState(current,Set.copyOf(next)));
  }}
 }
 record WorkflowState(IncidentStatus current,Set<IncidentStatus> allowed) implements IncidentState {
  public void checkTransition(IncidentStatus next){if(!allowed.contains(next))throw new BusinessException("Cannot move from "+current+" to "+next+".");}
 }
}
