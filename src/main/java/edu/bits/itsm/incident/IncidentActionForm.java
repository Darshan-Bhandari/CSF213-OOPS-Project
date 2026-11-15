package edu.bits.itsm.incident;

import jakarta.validation.constraints.*;

public class IncidentActionForm {
  @Min(value = 0, message = "Reload the incident before submitting an action.") private long version;
  @NotBlank(message = "Choose an incident action.")
  @Pattern(regexp = "ACKNOWLEDGE|START|RESOLVE|VERIFY|CLOSE|VERIFY_CLOSE|REJECT", message = "Choose a valid incident action.")
  private String action = "";
  @Size(max = 8000, message = "Resolution details must be at most 8000 characters.")
  private String details = "";
  public long getVersion() { return version; }
  public void setVersion(long value) { version = value; }
  public String getAction() { return action; }
  public void setAction(String value) { action = value; }
  public String getDetails() { return details; }
  public void setDetails(String value) { details = value; }
}
