package edu.bits.itsm.config;

import edu.bits.itsm.identity.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountRepository accounts)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/login", "/register", "/css/**", "/js/**", "/error")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/technician/**")
                    .hasRole("TECHNICIAN")
                    .requestMatchers("/employee/**")
                    .hasRole("EMPLOYEE")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .usernameParameter("email")
                    .defaultSuccessUrl("/", true)
                    .permitAll())
        .logout(
            logout ->
                logout
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
        .exceptionHandling(errors -> errors.accessDeniedPage("/access-denied"))
        .addFilterBefore(new AccountStatusFilter(accounts), AuthorizationFilter.class);
    return http.build();
  }
}
