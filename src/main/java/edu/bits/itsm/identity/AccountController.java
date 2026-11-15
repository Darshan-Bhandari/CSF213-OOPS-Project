package edu.bits.itsm.identity;

import edu.bits.itsm.shared.BusinessException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
  private final AccountService service;
  private final UserAccountRepository accounts;

  public AccountController(AccountService service, UserAccountRepository accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }

  @GetMapping("/register")
  String registration(Model model) {
    model.addAttribute("form", new RegistrationForm());
    return "register";
  }

  @PostMapping("/register")
  String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult errors) {
    if (errors.hasErrors()) return "register";
    try {
      service.register(form.getDisplayName(), form.getEmail(), form.getPassword());
    } catch (BusinessException ex) {
      errors.reject("registration", ex.getMessage());
      return "register";
    }
    return "redirect:/login?registered";
  }

  @GetMapping("/admin/users")
  String users(Model model) {
    model.addAttribute("accounts", accounts.findAllByOrderByCreatedAtDesc());
    model.addAttribute("form", new AdminAccountForm());
    return "admin/users";
  }

  @PostMapping("/admin/users")
  String create(
      @Valid @ModelAttribute("form") AdminAccountForm form,
      BindingResult errors,
      @AuthenticationPrincipal AccountPrincipal actor,
      Model model,
      RedirectAttributes flash) {
    if (!errors.hasErrors())
      try {
        service.createByAdmin(
            form.getDisplayName(),
            form.getEmail(),
            form.getPassword(),
            form.getRole(),
            actor.getAccountId());
        flash.addFlashAttribute("success", "Account created.");
        return "redirect:/admin/users";
      } catch (BusinessException ex) {
        errors.reject("account", ex.getMessage());
      }
    model.addAttribute("accounts", accounts.findAllByOrderByCreatedAtDesc());
    return "admin/users";
  }

  @PostMapping("/admin/users/{id}/active")
  String active(
      @PathVariable long id,
      @RequestParam boolean active,
      @AuthenticationPrincipal AccountPrincipal actor,
      RedirectAttributes flash) {
    try {
      service.setActive(id, active, actor.getAccountId());
      flash.addFlashAttribute("success", "Account status updated.");
    } catch (BusinessException ex) {
      flash.addFlashAttribute("error", ex.getMessage());
    }
    return "redirect:/admin/users";
  }
}
