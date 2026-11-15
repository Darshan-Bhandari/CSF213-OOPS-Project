package edu.bits.itsm.incident;
import jakarta.persistence.*;
import edu.bits.itsm.shared.BusinessException;
@Embeddable
public class PriorityAssessment {
 private int impact,urgency,criticality,score;
 private String priority;
 protected PriorityAssessment(){}
 public PriorityAssessment(int impact,int urgency,int criticality){
  if(impact<1||impact>3||urgency<1||urgency>3||criticality<1||criticality>3)throw new BusinessException("Priority factors must be between 1 and 3.");
  this.impact=impact;this.urgency=urgency;this.criticality=criticality;score=impact*urgency*criticality;
  priority=score>=18?"P1":score>=8?"P2":score>=3?"P3":"P4";
 }
 public int getImpact(){return impact;}public int getUrgency(){return urgency;}public int getCriticality(){return criticality;}public int getScore(){return score;}public String getPriority(){return priority;}
}
