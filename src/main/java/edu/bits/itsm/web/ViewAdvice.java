package edu.bits.itsm.web;

import edu.bits.itsm.identity.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class ViewAdvice {
  @ModelAttribute("viewer")
  public AccountPrincipal viewer(@AuthenticationPrincipal AccountPrincipal principal) {
    return principal;
  }
}
