package edu.bits.itsm;
import edu.bits.itsm.identity.*;
import edu.bits.itsm.identity.Role;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties="app.sla.monitor-enabled=false") @AutoConfigureMockMvc
class PasswordRecoveryIntegrationTest {
 static final EmbeddedPostgres DB=start();
 static EmbeddedPostgres start(){try{return EmbeddedPostgres.builder().setServerConfig("unix_socket_directories", "").setPort(0).start();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @AfterAll static void stop() throws Exception{DB.close();}
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->DB.getJdbcUrl("postgres","postgres"));r.add("spring.datasource.username",()->"postgres");r.add("spring.datasource.password",()->"");}
 @TestConfiguration static class Time { @Bean @Primary TestClock recoveryClock(){return new TestClock();} }
 static class TestClock extends Clock {Instant now=Instant.parse("2026-11-21T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;} }
 @Autowired PasswordRecovery recovery; @Autowired JdbcTemplate jdbc; @Autowired AccountService accounts;
 @Autowired UserAccountRepository users; @Autowired PasswordEncoder encoder; @Autowired TestClock clock; @Autowired MockMvc mvc;
 @MockitoBean RecoveryDelivery delivery;
 AtomicReference<String> link=new AtomicReference<>(); long id;
 @BeforeEach void setup(){
  clock.now=Instant.parse("2026-11-21T00:00:00Z");
  jdbc.execute("TRUNCATE user_accounts RESTART IDENTITY CASCADE");jdbc.execute("DELETE FROM password_reset_limits");jdbc.update("UPDATE password_reset_gate SET requests=0,window_start=?",java.sql.Timestamp.from(clock.now));
  id=accounts.register("Recovery Test","recover@campus.test","OldPassword123").getId();
  doAnswer(i->{link.set(i.getArgument(1));return null;}).when(delivery).send(anyString(),anyString());
 }
 String token(){return link.get().substring(link.get().indexOf("token=")+6);}
 void issue(){recovery.request(" ReCoVeR@campus.test ");assertNotNull(link.get());}
 long tokens(){return jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens",Long.class);}
 @Test void issuesOnlyHashAndGetDoesNotChangePassword() throws Exception {
  issue();assertEquals(43,token().length());assertEquals(1,tokens());assertNotEquals(token(),jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens",String.class));
  mvc.perform(get("/reset-password").param("token",token())).andExpect(status().isOk()).andExpect(header().string("Referrer-Policy","no-referrer")).andExpect(header().string("Cache-Control","no-store"));
  assertTrue(encoder.matches("OldPassword123",users.findById(id).orElseThrow().getPasswordHash()));
 }
 @Test void changesHashConsumesTokenAndPreservesAccount(){issue();String t=token();recovery.reset(t,"NewPassword123","NewPassword123");assertEquals(0,tokens());var u=users.findById(id).orElseThrow();assertTrue(encoder.matches("NewPassword123",u.getPasswordHash()));assertFalse(encoder.matches("OldPassword123",u.getPasswordHash()));assertEquals(1,u.getCredentialRevision());assertEquals(Role.EMPLOYEE,u.getRole());assertThrows(RuntimeException.class,()->recovery.reset(t,"AnotherPass123","AnotherPass123"));}
 @Test void expiryAtExactlyFifteenMinutesRejects(){issue();clock.now=clock.now.plusSeconds(900);assertThrows(RuntimeException.class,()->recovery.reset(token(),"NewPassword123","NewPassword123"));}
 @Test void mismatchAndByteLimitDoNotConsumeToken(){issue();assertThrows(RuntimeException.class,()->recovery.reset(token(),"NewPassword123","Mismatch123"));String huge="é".repeat(40);assertThrows(RuntimeException.class,()->recovery.reset(token(),huge,huge));assertEquals(1,tokens());}
 @Test void newerRequestInvalidatesOlder(){issue();String old=token();clock.now=clock.now.plusSeconds(61);issue();assertThrows(RuntimeException.class,()->recovery.reset(old,"NewPassword123","NewPassword123"));recovery.reset(token(),"NewPassword123","NewPassword123");}
 @Test void inactiveAndUnknownHaveNoDelivery(){recovery.request("unknown@campus.test");jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",id);recovery.request("recover@campus.test");verifyNoInteractions(delivery);assertEquals(0,tokens());}
 @Test void deactivationAfterIssuePreventsReset(){issue();jdbc.update("UPDATE user_accounts SET active=false WHERE id=?",id);assertThrows(RuntimeException.class,()->recovery.reset(token(),"NewPassword123","NewPassword123"));}
 @Test void emailCooldownAndGlobalLimitApplyToUnknownToo(){issue();recovery.request("recover@campus.test");verify(delivery,times(1)).send(anyString(),anyString());for(int n=0;n<25;n++)recovery.request("absent"+n+"@campus.test");assertEquals(20,jdbc.queryForObject("SELECT requests FROM password_reset_gate",Integer.class));}
 @Test void deliveryFailureInvalidatesToken(){doThrow(new IllegalStateException("private provider failure")).when(delivery).send(anyString(),anyString());assertDoesNotThrow(()->recovery.request("recover@campus.test"));assertEquals(0,tokens());}
 @Test void anonymousFormsAndCsrf() throws Exception {
  mvc.perform(get("/forgot-password")).andExpect(status().isOk());
  mvc.perform(post("/forgot-password").param("email","recover@campus.test")).andExpect(status().isForbidden());
  mvc.perform(post("/forgot-password").with(csrf()).param("email","unknown@campus.test")).andExpect(status().isOk()).andExpect(model().attribute("sent",true));
  mvc.perform(post("/forgot-password").with(csrf()).param("email","recover@campus.test")).andExpect(status().isOk()).andExpect(model().attribute("sent",true));
  mvc.perform(post("/reset-password").param("token",token()).param("password","NewPassword123").param("confirmation","NewPassword123")).andExpect(status().isForbidden());
  mvc.perform(post("/reset-password").with(csrf()).param("token",token()).param("password","NewPassword123").param("confirmation","NewPassword123")).andExpect(redirectedUrl("/login?reset"));
 }
 @Test void oldSessionsAreRevokedOnNextRequest() throws Exception {
  var session=new org.springframework.mock.web.MockHttpSession();
  var principal=new AccountPrincipal(users.findById(id).orElseThrow());
  var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
  session.setAttribute("SPRING_SECURITY_CONTEXT",new org.springframework.security.core.context.SecurityContextImpl(auth));
  issue();recovery.reset(token(),"NewPassword123","NewPassword123");mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login?expired"));
 }
 @Test void onlyOneConcurrentResetWins() throws Exception {
  issue();String t=token();var pool=Executors.newFixedThreadPool(2);try{
   Callable<Boolean> task=()->{try{recovery.reset(t,"NewPassword123","NewPassword123");return true;}catch(RuntimeException e){return false;}};
   var futures=pool.invokeAll(List.of(task,task));int success=0;for(var f:futures)if(f.get())success++;assertEquals(1,success);
  }finally{pool.shutdownNow();}
 }
 @Test void auditFailureRollsBackPasswordAndConsumption(){issue();String t=token();jdbc.execute("ALTER TABLE audit_entries ADD CONSTRAINT reject_reset_test CHECK(action<>'PASSWORD_RESET')");try{assertThrows(RuntimeException.class,()->recovery.reset(t,"NewPassword123","NewPassword123"));assertEquals(1,tokens());assertTrue(encoder.matches("OldPassword123",users.findById(id).orElseThrow().getPasswordHash()));}finally{jdbc.execute("ALTER TABLE audit_entries DROP CONSTRAINT reject_reset_test");}}
 @Test void malformedTokenRejected(){for(String t:List.of("","invalid","x".repeat(43)))assertThrows(RuntimeException.class,()->recovery.reset(t,"NewPassword123","NewPassword123"));}
}
