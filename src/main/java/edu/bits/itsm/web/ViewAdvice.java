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
  @ModelAttribute("navSection")
  public String navSection(jakarta.servlet.http.HttpServletRequest request) {
    String uri = request.getRequestURI().substring(request.getContextPath().length());
    if (uri.equals("/") || uri.endsWith("/dashboard")) return "overview";
    if (uri.startsWith("/admin/users")) return "users";
    if (uri.startsWith("/admin/catalog")) return "catalog";
    if (uri.startsWith("/admin/audit")) return "audit";
    if (uri.startsWith("/admin/email")) return "email";
    if (uri.startsWith("/notifications")) return "notifications";
    if (uri.contains("/incidents")) return "incidents";
    return "";
  }

}
