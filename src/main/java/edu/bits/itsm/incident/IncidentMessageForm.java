package edu.bits.itsm.incident;

import jakarta.validation.constraints.*;

public class IncidentMessageForm {
  @Min(value = 0, message = "Reload the incident before posting.") private long version;
  @NotBlank(message = "Choose a message type.")
  @Pattern(regexp = "PUBLIC|INTERNAL|REQUEST", message = "Choose a valid message type.")
  private String kind = "PUBLIC";
  @NotBlank(message = "Enter a message.")
  @Size(max = 4000, message = "Message must be at most 4000 characters.")
  private String body = "";
  @Positive(message = "Choose a valid information request.") private Long responseTo;

  public long getVersion() { return version; }
  public void setVersion(long value) { version = value; }
  public String getKind() { return kind; }
  public void setKind(String value) { kind = value; }
  public String getBody() { return body; }
  public void setBody(String value) { body = value; }
  public Long getResponseTo() { return responseTo; }
  public void setResponseTo(Long value) { responseTo = value; }
}
