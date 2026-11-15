package edu.bits.itsm.sla;

import java.time.*;

/** Exact target evaluation is separate from scheduler frequency and persistence. */
public record SlaTarget(Instant start, Instant deadline, Instant completed) {
  public enum Outcome {
    NONE,
    WARNING,
    BREACH
  }

  public Outcome evaluate(Instant now) {
    if (completed != null) return completed.isAfter(deadline) ? Outcome.BREACH : Outcome.NONE;
    if (now.isAfter(deadline)) return Outcome.BREACH;
    return now.isBefore(start.plusMillis(Duration.between(start, deadline).toMillis() * 80 / 100))
        ? Outcome.NONE
        : Outcome.WARNING;
  }
}
