package edu.bits.itsm;

import static org.junit.jupiter.api.Assertions.*;

import edu.bits.itsm.ai.*;
import edu.bits.itsm.assignment.*;
import edu.bits.itsm.incident.*;
import edu.bits.itsm.shared.BusinessException;
import edu.bits.itsm.sla.SlaTarget;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkflowRulesTest {
  private final Instant start = Instant.parse("2026-11-15T10:00:00Z");

  private Incident incident() {
    return new Incident(
        "Network unavailable",
        "Cannot connect",
        1,
        1,
        1,
        20,
        new PriorityAssessment(3, 3, 3),
        start,
        300,
        1800);
  }

  @ParameterizedTest
  @CsvSource({
    "1,1,1,P4",
    "1,2,1,P4",
    "1,3,1,P3",
    "2,2,1,P3",
    "2,2,2,P2",
    "3,3,1,P2",
    "3,3,2,P1",
    "3,3,3,P1"
  })
  void priorityExamples(int impact, int urgency, int criticality, String expected) {
    assertEquals(expected, new PriorityAssessment(impact, urgency, criticality).getPriority());
  }

  @ParameterizedTest
  @CsvSource({
      "1,1,1,P4", "1,1,2,P4", "1,1,3,P3",
      "1,2,1,P4", "1,2,2,P3", "1,2,3,P3",
      "1,3,1,P3", "1,3,2,P3", "1,3,3,P2",
      "2,1,1,P4", "2,1,2,P3", "2,1,3,P3",
      "2,2,1,P3", "2,2,2,P2", "2,2,3,P2",
      "2,3,1,P3", "2,3,2,P2", "2,3,3,P1",
      "3,1,1,P3", "3,1,2,P3", "3,1,3,P2",
      "3,2,1,P3", "3,2,2,P2", "3,2,3,P1",
      "3,3,1,P2", "3,3,2,P1", "3,3,3,P1"
  })
  void everyPriorityCombinationMatchesApprovedTable(int i, int u, int c, String expected) {
    var assessment = new PriorityAssessment(i, u, c);
    assertEquals(expected, assessment.getPriority());
    assertEquals(i * u * c, assessment.getScore());
  }

  @Test
  void everyLifecyclePairMatchesApprovedGraph() {
    Set<String> allowed = Set.of("NEW:CLASSIFIED", "CLASSIFIED:ASSIGNED",
        "ASSIGNED:ACKNOWLEDGED", "ACKNOWLEDGED:IN_PROGRESS", "IN_PROGRESS:RESOLVED",
        "RESOLVED:VERIFIED", "VERIFIED:CLOSED", "ASSIGNED:CLASSIFIED",
        "ACKNOWLEDGED:CLASSIFIED", "IN_PROGRESS:CLASSIFIED", "RESOLVED:CLASSIFIED");
    for (var from : IncidentStatus.values()) for (var to : IncidentStatus.values()) {
      if (allowed.contains(from + ":" + to))
        assertDoesNotThrow(() -> IncidentState.forStatus(from).checkTransition(to), from + ":" + to);
      else assertThrows(BusinessException.class,
          () -> IncidentState.forStatus(from).checkTransition(to), from + ":" + to);
    }
  }

  @Test
  void allPriorityInputsAreMonotonic() {
    for (int i = 1; i <= 3; i++)
      for (int u = 1; u <= 3; u++)
        for (int c = 1; c <= 3; c++) {
          var p = new PriorityAssessment(i, u, c);
          assertEquals(i * u * c, p.getScore());
          if (i < 3) assertTrue(new PriorityAssessment(i + 1, u, c).getScore() > p.getScore());
        }
  }

  @ParameterizedTest
  @CsvSource({"0,1,1", "4,1,1", "1,0,1", "1,4,1", "1,1,0", "1,1,4", "1,-1,1"})
  void invalidPriorityFactors(int i, int u, int c) {
    assertThrows(BusinessException.class, () -> new PriorityAssessment(i, u, c));
  }

  @Test
  void lifecycleGuardsAndClosure() {
    var i = incident();
    assertThrows(BusinessException.class, () -> i.transition(IncidentStatus.CLOSED, start));
    i.transition(IncidentStatus.CLASSIFIED, start);
    i.assign(2, start);
    assertThrows(BusinessException.class, () -> i.transition(IncidentStatus.RESOLVED, start));
    i.transition(IncidentStatus.ACKNOWLEDGED, start.plusSeconds(10));
    i.transition(IncidentStatus.IN_PROGRESS, start.plusSeconds(20));
    i.transition(IncidentStatus.RESOLVED, start.plusSeconds(30));
    i.transition(IncidentStatus.VERIFIED, start.plusSeconds(40));
    i.transition(IncidentStatus.CLOSED, start.plusSeconds(50));
    assertFalse(i.getStatus().isActive());
    assertThrows(BusinessException.class, () -> i.unassign(start));
  }

  @Test
  void rejectionPreservesFirstResponseAndResolution() {
    var i = incident();
    i.transition(IncidentStatus.CLASSIFIED, start);
    i.assign(2, start);
    i.transition(IncidentStatus.ACKNOWLEDGED, start.plusSeconds(5));
    i.transition(IncidentStatus.IN_PROGRESS, start.plusSeconds(6));
    i.transition(IncidentStatus.RESOLVED, start.plusSeconds(7));
    i.reject(start.plusSeconds(10));
    assertEquals(2, i.getCycle());
    assertNull(i.getAssigneeId());
    assertNull(i.getResolvedAt());
    assertEquals(start.plusSeconds(7), i.getFirstResolvedAt());
    assertEquals(start.plusSeconds(5), i.getAcknowledgedAt());
    assertEquals(start.plusSeconds(1810), i.getResolutionDue());
  }

  @Test
  void reprioritizationDoesNotResetElapsedTime() {
    var i = incident();
    i.transition(IncidentStatus.CLASSIFIED, start);
    i.correct(2, 2, 2, start.plusSeconds(100), 100, 200);
    assertEquals(start.plusSeconds(100), i.getResponseDue());
    assertEquals(start.plusSeconds(200), i.getResolutionDue());
    assertEquals(3, i.getAssessment().getCriticality());
  }

  @Test
  void strategiesSelectDifferentQualifiedCandidates() {
    var candidates =
        List.of(
            new AssignmentStrategy.Candidate(1, 3, 4, 5),
            new AssignmentStrategy.Candidate(2, 2, 0, 5));
    assertEquals(1, new SkillBasedAssignment().select(candidates).orElseThrow().userId());
    assertEquals(2, new LoadBasedAssignment().select(candidates).orElseThrow().userId());
  }

  @Test
  void tiesAndEmptyCandidateList() {
    var candidates =
        List.of(
            new AssignmentStrategy.Candidate(7, 2, 0, 5),
            new AssignmentStrategy.Candidate(3, 2, 0, 5));
    assertEquals(3, new SkillBasedAssignment().select(candidates).orElseThrow().userId());
    assertEquals(3, new LoadBasedAssignment().select(candidates).orElseThrow().userId());
    assertTrue(new LoadBasedAssignment().select(List.of()).isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"79,NONE", "80,WARNING", "100,WARNING", "101,BREACH"})
  void exactSlaBoundaries(int seconds, String expected) {
    assertEquals(
        expected,
        new SlaTarget(start, start.plusSeconds(100), null)
            .evaluate(start.plusSeconds(seconds))
            .name());
  }

  @Test
  void onTimeAndLateCompletionsRemainStableAfterDeadline() {
    assertEquals(
        SlaTarget.Outcome.NONE,
        new SlaTarget(start, start.plusSeconds(100), start.plusSeconds(100))
            .evaluate(start.plusSeconds(1000)));
    assertEquals(
        SlaTarget.Outcome.BREACH,
        new SlaTarget(start, start.plusSeconds(100), start.plusSeconds(101))
            .evaluate(start.plusSeconds(1000)));
  }

  @Test
  void invalidIncidentDescription() {
    assertThrows(
        BusinessException.class,
        () ->
            new Incident(
                "", "Description", 1, 1, 1, 1, new PriorityAssessment(1, 1, 1), start, 10, 20));
  }

  @Test
  void missingAiConfigAndSecretRedaction() {
    var config = new AiConfiguration("secret-test-token", "");
    assertFalse(config.isConfigured());
    assertFalse(config.toString().contains("secret-test-token"));
    assertThrows(
        BusinessException.class,
        () ->
            new OpenRouterAdapter(config, new com.fasterxml.jackson.databind.ObjectMapper())
                .complete(Map.of()));
  }
}
