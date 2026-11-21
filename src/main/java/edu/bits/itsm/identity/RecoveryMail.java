package edu.bits.itsm.identity;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class RecoveryMail implements RecoveryDelivery {
  private final ObjectProvider<JavaMailSender> senders;
  private final String mode, from, directory;
  public RecoveryMail(ObjectProvider<JavaMailSender> senders,
      @Value("${app.recovery.mode:disabled}") String mode,
      @Value("${app.recovery.from:}") String from,
      @Value("${app.recovery.directory:.local/recovery-mail}") String directory,
      @Value("${server.address:127.0.0.1}") String address) {
    if (!java.util.Set.of("disabled","local","smtp").contains(mode)) throw new IllegalArgumentException("Unknown recovery mode");
    if(mode.equals("local") && !java.util.Set.of("127.0.0.1","localhost","::1").contains(address)) throw new IllegalArgumentException("Local recovery requires loopback binding");
    this.senders=senders; this.mode=mode; this.from=from; this.directory=directory;
  }
  public void send(String email,String link) {
    if (mode.equals("smtp")) {
      var sender=senders.getIfAvailable();
      if(sender==null || from.isBlank()) throw new IllegalStateException("Recovery sender unavailable");
      var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
      message.setSubject("Reset your BITSOps password");
      message.setText("Choose a new password using this single-use link within 15 minutes:\n"+link+"\nIf you did not request this, ignore this email.");
      sender.send(message);
    } else if(mode.equals("local")) {
      try {
        Path dir=Path.of(directory);Files.createDirectories(dir);
        Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));
        Path file=Files.createTempFile(dir,"reset-",".txt",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file,"LOCAL DEMO ONLY\nRecipient: "+email+"\nReset link: "+link);
      } catch(Exception e) {throw new IllegalStateException("Local recovery delivery unavailable");}
    } else throw new IllegalStateException("Recovery disabled");
  }
}
