package edu.bits.itsm.identity;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
  private final long accountId;
  private final String displayName;
  private final Role role;

  public AccountPrincipal(UserAccount account) {
    super(
        account.getEmail(),
        account.getPasswordHash(),
        account.isActive(),
        true,
        true,
        true,
        List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
    accountId = account.getId();
    displayName = account.getDisplayName();
    role = account.getRole();
  }

  public long getAccountId() {
    return accountId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Role getRole() {
    return role;
  }
}
