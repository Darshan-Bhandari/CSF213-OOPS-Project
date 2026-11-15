package edu.bits.itsm.shared;

public class ConflictException extends BusinessException {
  public ConflictException() {
    super("This incident changed. Refresh the page before trying again.");
  }
}
