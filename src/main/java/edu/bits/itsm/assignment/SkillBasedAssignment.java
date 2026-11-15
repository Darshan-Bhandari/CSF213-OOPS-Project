package edu.bits.itsm.assignment;
import java.util.*;import org.springframework.stereotype.Component;
@Component public class SkillBasedAssignment implements AssignmentStrategy {
 public String key(){return "SKILL";}
 public Optional<Candidate> select(List<Candidate> candidates){return candidates.stream().min(Comparator.comparingInt(Candidate::proficiency).reversed().thenComparingInt(Candidate::workload).thenComparingLong(Candidate::userId));}
}
