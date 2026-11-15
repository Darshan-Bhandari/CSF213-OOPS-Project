package edu.bits.itsm.assignment;
import java.util.*;
public interface AssignmentStrategy {
 String key(); Optional<Candidate> select(List<Candidate> candidates);
 record Candidate(long userId,int proficiency,int workload,int capacity){}
}
