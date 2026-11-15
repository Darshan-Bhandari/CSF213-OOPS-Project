package edu.bits.itsm.identity;

import jakarta.validation.constraints.NotNull;

public class AdminAccountForm extends RegistrationForm {
  @NotNull private Role role = Role.EMPLOYEE;

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }
}
