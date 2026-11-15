package edu.bits.itsm.incident;

import jakarta.validation.constraints.*;

public class IncidentFeedbackForm {
  @NotNull(message = "Choose a rating from 1 to 5.")
  @Min(value = 1, message = "Rating must be between 1 and 5.")
  @Max(value = 5, message = "Rating must be between 1 and 5.")
  private Integer rating = 5;
  @Size(max = 2000, message = "Feedback comments must be at most 2000 characters.")
  private String comment = "";
  public Integer getRating() { return rating; }
  public void setRating(Integer value) { rating = value; }
  public String getComment() { return comment; }
  public void setComment(String value) { comment = value; }
}
