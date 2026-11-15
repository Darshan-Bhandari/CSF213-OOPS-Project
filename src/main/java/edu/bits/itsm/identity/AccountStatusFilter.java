package edu.bits.itsm.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountStatusFilter extends OncePerRequestFilter {
  private final UserAccountRepository accounts;

  public AccountStatusFilter(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AccountPrincipal principal) {
      var current = accounts.findById(principal.getAccountId());
      if (current.isEmpty()
          || !current.get().isActive()
          || current.get().getRole() != principal.getRole()) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect(request.getContextPath() + "/login?expired");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
