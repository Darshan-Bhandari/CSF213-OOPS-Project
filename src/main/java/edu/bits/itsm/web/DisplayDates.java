package edu.bits.itsm.web;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component("dates")
public class DisplayDates {
  private final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

  public String format(Object value) {
    if (value == null) return "—";
    if (value instanceof Instant instant) return formatter.format(instant);
    if (value instanceof java.util.Date date) return formatter.format(date.toInstant());
    if (value instanceof OffsetDateTime time) return formatter.format(time.toInstant());
    return value.toString();
  }
}
