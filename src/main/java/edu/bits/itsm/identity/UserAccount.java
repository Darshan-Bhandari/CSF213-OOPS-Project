package edu.bits.itsm.identity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private long credentialRevision;
  public long getCredentialRevision() { return credentialRevision; }

  @Column(nullable = false, length = 100)
  private String displayName;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Version private long version;

  protected UserAccount() {}

  public UserAccount(String displayName, String email, String passwordHash, Role role) {
    this.displayName = displayName;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public Long getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void changeActive(boolean active) {
    this.active = active;
  }
}
