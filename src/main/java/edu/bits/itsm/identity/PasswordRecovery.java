package edu.bits.itsm.identity;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import edu.bits.itsm.audit.AuditService;
import edu.bits.itsm.shared.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecovery {
  private final JdbcTemplate jdbc; private final PasswordEncoder encoder;
  private final RecoveryDelivery delivery; private final Clock clock; private final AuditService audit;
  private final String base;
  public PasswordRecovery(JdbcTemplate jdbc,PasswordEncoder encoder,RecoveryDelivery delivery,Clock clock,AuditService audit,
      @Value("${app.recovery.base-url:http://127.0.0.1:8080}") String base) {
    this.jdbc=jdbc;this.encoder=encoder;this.delivery=delivery;this.clock=clock;this.audit=audit;
    var uri=java.net.URI.create(base);
    if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
      throw new IllegalArgumentException("Invalid recovery base URL");
    if(uri.getScheme().equals("http") && !Set.of("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost()))
      throw new IllegalArgumentException("Recovery requires HTTPS outside localhost");
    this.base=base.replaceAll("/+$", "");
  }
  static String hash(String value) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
  @Transactional
  public void request(String rawEmail) {
    String email=rawEmail==null?"":rawEmail.strip().toLowerCase(Locale.ROOT);
    if(email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))return;
    Instant now=clock.instant();
    var gate=jdbc.queryForMap("SELECT * FROM password_reset_gate WHERE id=1 FOR UPDATE");
    Instant start=((Timestamp)gate.get("window_start")).toInstant();
    int count=((Number)gate.get("requests")).intValue();
    if(!now.isBefore(start.plusSeconds(60))) {start=now;count=0;}
    if(count>=20)return;
    jdbc.update("UPDATE password_reset_gate SET window_start=?,requests=? WHERE id=1",Timestamp.from(start),count+1);
    String key=hash(email);
    int accepted=jdbc.update("INSERT INTO password_reset_limits(email_hash,requested_at) VALUES (?,?) ON CONFLICT(email_hash) DO UPDATE SET requested_at=excluded.requested_at WHERE password_reset_limits.requested_at<=?",key,Timestamp.from(now),Timestamp.from(now.minusSeconds(60)));
    if(accepted==0)return;
    jdbc.update("DELETE FROM password_reset_limits WHERE requested_at<?",Timestamp.from(now.minusSeconds(86400)));
    jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at<=?",Timestamp.from(now));
    var users=jdbc.queryForList("SELECT id FROM user_accounts WHERE email=? AND active FOR UPDATE",email);
    if(users.isEmpty())return;
    long user=((Number)users.getFirst().get("id")).longValue();
    byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?",user);
    jdbc.update("INSERT INTO password_reset_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",hash(token),user,Timestamp.from(now.plusSeconds(900)));
    // No token or recipient in logs/audit. Delivery failure invalidates this link.
    try {delivery.send(email,base+"/reset-password?token="+token);}
    catch(RuntimeException failure){jdbc.update("DELETE FROM password_reset_tokens WHERE token_hash=?",hash(token));}
  }
  @Transactional
  public void reset(String token,String password,String confirmation) {
    if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
    if(password==null || !password.equals(confirmation) || password.length()<8 || password.length()>64 || password.getBytes(StandardCharsets.UTF_8).length>72)
      throw new BusinessException("Use matching passwords of 8–64 characters and at most 72 UTF-8 bytes.");
    String digest=hash(token);
    var candidates=jdbc.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash=?",digest);
    if(candidates.isEmpty())throw invalid();
    long user=((Number)candidates.getFirst().get("user_id")).longValue();
    var accounts=jdbc.queryForList("SELECT id FROM user_accounts WHERE id=? AND active FOR UPDATE",user);
    if(accounts.isEmpty())throw invalid();
    var rows=jdbc.queryForList("SELECT expires_at,used FROM password_reset_tokens WHERE token_hash=? FOR UPDATE",digest);
    if(rows.isEmpty() || Boolean.TRUE.equals(rows.getFirst().get("used")) || !clock.instant().isBefore(((Timestamp)rows.getFirst().get("expires_at")).toInstant()))throw invalid();
    jdbc.update("UPDATE user_accounts SET password_hash=?,credential_revision=credential_revision+1,version=version+1 WHERE id=?",encoder.encode(password),user);
    jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?",user);
    audit.record(user,"PASSWORD_RESET","USER-"+user,"Password changed through a single-use recovery link; sessions revoked on next request");
  }
  private BusinessException invalid(){return new BusinessException("This reset link is invalid, expired or already used. Request a new link.");}
}
