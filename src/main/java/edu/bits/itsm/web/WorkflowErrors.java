package edu.bits.itsm.web;

import edu.bits.itsm.shared.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class WorkflowErrors {
  @ExceptionHandler(BusinessException.class)
  ModelAndView business(BusinessException ex) {
    var view = new ModelAndView("error/workflow");
    view.setStatus(ex instanceof ConflictException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
    view.addObject("message", ex.getMessage());
    return view;
  }

  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ModelAndView database(org.springframework.dao.DataAccessException ex) {
    var view = new ModelAndView("error/workflow");
    view.setStatus(HttpStatus.CONFLICT);
    view.addObject(
        "message",
        "The change could not be saved. It may conflict with existing data, or storage may be"
            + " unavailable. Refresh and check your input before retrying.");
    return view;
  }

  @ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
  ModelAndView stale(Exception ex) {
    return business(new ConflictException());
  }
}
