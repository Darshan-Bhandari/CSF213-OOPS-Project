package edu.bits.itsm.identity;

import java.util.Locale;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {
  private final UserAccountRepository accounts;

  public AccountUserDetailsService(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    return accounts
        .findByEmail(username.strip().toLowerCase(Locale.ROOT))
        .map(AccountPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }
}
