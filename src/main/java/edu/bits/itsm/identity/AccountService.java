package edu.bits.itsm.identity;

import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.shared.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final UserAccountRepository accounts;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final JdbcTemplate jdbc;

  public AccountService(
      UserAccountRepository accounts,
      PasswordEncoder encoder,
      AuditService audit,
      JdbcTemplate jdbc) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  @Transactional
  public UserAccount register(String name, String email, String password) {
    return create(name, email, password, Role.EMPLOYEE, null);
  }

  @Transactional
  public UserAccount createByAdmin(
      String name, String email, String password, Role role, long actor) {
    requireAdmin(actor);
    return create(name, email, password, role, actor);
  }

  private void requireAdmin(long actor) {
    var admin =
        accounts.findById(actor).orElseThrow(() -> new BusinessException("Account not found."));
    if (!admin.isActive() || admin.getRole() != Role.ADMIN)
      throw new BusinessException("Administrator access required.");
  }

  private UserAccount create(String name, String email, String password, Role role, Long actor) {
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    if (name.isBlank()
        || name.strip().length() > 100
        || normalized.length() > 254
        || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new BusinessException("Enter a valid name and email address.");
    if (password.length() < 8
        || password.length() > 64
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("Password must be 8–64 characters and at most 72 UTF-8 bytes.");
    if (accounts.existsByEmail(normalized))
      throw new BusinessException("An account already uses that email.");
    UserAccount created;
    try {
      created =
          accounts.saveAndFlush(
              new UserAccount(name.strip(), normalized, encoder.encode(password), role));
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException("Unable to create account; check the email and try again.");
    }
    if (role == Role.TECHNICIAN)
      jdbc.update("INSERT INTO technician_profiles(user_id) VALUES (?)", created.getId());
    audit.record(
        actor, "ACCOUNT_CREATED", "USER-" + created.getId(), "Created " + role.name() + " account");
    return created;
  }

  @Transactional
  public void setActive(long target, boolean active, long actor) {
    requireAdmin(actor);
    if (target == actor && !active)
      throw new BusinessException("You cannot deactivate your own account.");
    var account =
        accounts.findById(target).orElseThrow(() -> new BusinessException("Account not found."));
    boolean before = account.isActive();
    account.changeActive(active);
    if (before != active)
      audit.record(actor, "ACCOUNT_ACTIVE_CHANGED", "USER-" + target, before + " -> " + active);
  }
}
