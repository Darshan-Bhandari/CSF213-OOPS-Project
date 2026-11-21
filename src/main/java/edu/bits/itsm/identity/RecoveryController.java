package edu.bits.itsm.identity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import edu.bits.itsm.shared.BusinessException;
@Controller
public class RecoveryController {
 private final PasswordRecovery recovery;
 public RecoveryController(PasswordRecovery recovery){this.recovery=recovery;}
 private void secure(HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");response.setHeader("Content-Security-Policy","default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'");}
 @GetMapping("/forgot-password") public String forgot(HttpServletResponse response){secure(response);return "forgot-password";}
 @PostMapping("/forgot-password") public String request(@RequestParam(defaultValue="") String email,Model model,HttpServletResponse response){
  secure(response);recovery.request(email);model.addAttribute("sent",true);return "forgot-password";
 }
 @GetMapping("/reset-password") public String form(@RequestParam(defaultValue="") String token,Model model,HttpServletResponse response){secure(response);model.addAttribute("token",token.length()<=100?token:"");return "reset-password";}
 @PostMapping("/reset-password") public String reset(@RequestParam(defaultValue="") String token,@RequestParam(defaultValue="") String password,@RequestParam(defaultValue="") String confirmation,Model model,HttpServletResponse response){
  secure(response);
  try {recovery.reset(token,password,confirmation);return "redirect:/login?reset";}
  catch(BusinessException e){model.addAttribute("error",e.getMessage());model.addAttribute("token",token.length()<=100?token:"");return "reset-password";}
 }
}
