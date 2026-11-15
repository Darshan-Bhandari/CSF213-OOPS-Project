package edu.bits.itsm.incident;

import edu.bits.itsm.shared.BusinessException;
import java.util.*;

/** State objects own the allowed next steps; controllers cannot bypass them. */
public interface IncidentState {
  void checkTransition(IncidentStatus next);

  static IncidentState forStatus(IncidentStatus status) {
    return States.VALUES.get(status);
  }

  final class States {
    private static final Map<IncidentStatus, IncidentState> VALUES =
        new EnumMap<>(IncidentStatus.class);

    static {
      allow(IncidentStatus.NEW, IncidentStatus.CLASSIFIED);
      allow(IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED);
      allow(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.CLASSIFIED);
      allow(IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.CLASSIFIED);
      allow(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED, IncidentStatus.CLASSIFIED);
      allow(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLASSIFIED);
      allow(IncidentStatus.VERIFIED, IncidentStatus.CLOSED);
      allow(IncidentStatus.CLOSED);
    }

    private static void allow(IncidentStatus current, IncidentStatus... next) {
      VALUES.put(current, new WorkflowState(current, Set.of(next)));
    }
  }

  record WorkflowState(IncidentStatus current, Set<IncidentStatus> allowed)
      implements IncidentState {
    public void checkTransition(IncidentStatus next) {
      if (!allowed.contains(next))
        throw new BusinessException("Cannot move from " + current + " to " + next + ".");
    }
  }
}
