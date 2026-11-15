package edu.bits.itsm.web;

import edu.bits.itsm.shared.*;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = IncidentApi.class)
@Order(-10)
public class ApiErrors {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, String>> business(BusinessException e) {
    return ResponseEntity.status(
            e instanceof ConflictException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST)
        .body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, String>> denied() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "Incident access denied."));
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  public ResponseEntity<Map<String, String>> storage() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("error", "Storage is temporarily unavailable."));
  }
}
