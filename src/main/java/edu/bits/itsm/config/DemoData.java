package edu.bits.itsm.config;

import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.identity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {
  private final UserAccountRepository accounts;
  private final AccountService service;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final String password;

  public DemoData(
      UserAccountRepository accounts,
      AccountService service,
      PasswordEncoder encoder,
      AuditService audit,
      @Value("${app.demo.password}") String password) {
    this.accounts = accounts;
    this.service = service;
    this.encoder = encoder;
    this.audit = audit;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (password.isBlank())
      throw new IllegalStateException(
          "The demo profile requires DEMO_PASSWORD; no default password is shipped.");
    if (password.length() < 8
        || password.length() > 64
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "DEMO_PASSWORD must be 8–64 characters and at most 72 UTF-8 bytes.");
    var admin =
        accounts
            .findByEmail("admin@campus.test")
            .orElseGet(
                () -> {
                  var user =
                      accounts.saveAndFlush(
                          new UserAccount(
                              "Demo administrator",
                              "admin@campus.test",
                              encoder.encode(password),
                              Role.ADMIN));
                  audit.record(
                      null,
                      "DEMO_ACCOUNT_CREATED",
                      "USER-" + user.getId(),
                      "Created demo administrator");
                  return user;
                });
    if (!accounts.existsByEmail("employee@campus.test"))
      service.register("Demo employee", "employee@campus.test", password);
    if (!accounts.existsByEmail("technician@campus.test"))
      service.createByAdmin(
          "Demo technician", "technician@campus.test", password, Role.TECHNICIAN, admin.getId());
  }
}
