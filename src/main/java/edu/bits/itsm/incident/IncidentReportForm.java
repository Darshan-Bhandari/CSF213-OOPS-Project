package edu.bits.itsm.incident;

import jakarta.validation.constraints.*;

/** Only reporter-controlled inputs; priority, ownership and state are calculated by the backend. */
public class IncidentReportForm {
  @NotBlank(message = "Enter a short title.") @Size(max = 160, message = "Title must be at most 160 characters.") private String title = "";
  @NotBlank(message = "Describe the problem.") @Size(max = 8000, message = "Description must be at most 8000 characters.") private String description = "";
  @NotNull(message = "Choose an affected service.") @Positive(message = "Choose a valid service.") private Long service;
  @NotNull(message = "Choose a category.") @Positive(message = "Choose a valid category.") private Long category;
  @Min(value = 1, message = "Impact must be between 1 and 3.") @Max(value = 3, message = "Impact must be between 1 and 3.") private int impact = 1;
  @Min(value = 1, message = "Urgency must be between 1 and 3.") @Max(value = 3, message = "Urgency must be between 1 and 3.") private int urgency = 1;
  @Min(value = 1, message = "Affected users must be at least 1.") private int affected = 1;

  public String getTitle() { return title; }
  public void setTitle(String value) { title = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { description = value; }
  public Long getService() { return service; }
  public void setService(Long value) { service = value; }
  public Long getCategory() { return category; }
  public void setCategory(Long value) { category = value; }
  public int getImpact() { return impact; }
  public void setImpact(int value) { impact = value; }
  public int getUrgency() { return urgency; }
  public void setUrgency(int value) { urgency = value; }
  public int getAffected() { return affected; }
  public void setAffected(int value) { affected = value; }
}
