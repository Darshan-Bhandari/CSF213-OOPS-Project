package edu.bits.itsm.identity;

public enum Role {
  EMPLOYEE,
  TECHNICIAN,
  ADMIN;

  /** Human-readable label; persisted role names and authorization tokens stay stable. */
  public String getDisplayName() {
    return this == EMPLOYEE ? "Campus Community" : name();
  }
}
