package edu.bits.itsm.incident;

public enum IncidentStatus {
  NEW,
  CLASSIFIED,
  ASSIGNED,
  ACKNOWLEDGED,
  IN_PROGRESS,
  RESOLVED,
  VERIFIED,
  CLOSED;

  public boolean isActive() {
    return this != RESOLVED && this != VERIFIED && this != CLOSED;
  }
}
