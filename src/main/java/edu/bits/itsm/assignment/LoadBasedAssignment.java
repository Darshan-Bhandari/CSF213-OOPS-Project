package edu.bits.itsm.assignment;
import java.util.*;import org.springframework.stereotype.Component;
@Component public class LoadBasedAssignment implements AssignmentStrategy {
 public String key(){return "LOAD";}
 public Optional<Candidate> select(List<Candidate> candidates){return candidates.stream().min(Comparator.comparingInt(Candidate::workload).thenComparing(Comparator.comparingInt(Candidate::proficiency).reversed()).thenComparingLong(Candidate::userId));}
}
