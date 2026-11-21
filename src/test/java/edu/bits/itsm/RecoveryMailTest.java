package edu.bits.itsm;
import edu.bits.itsm.identity.RecoveryMail;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
class RecoveryMailTest {
 @TempDir Path directory;
 @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
 @Test void privateLocalFileContainsLinkButNoExternalSend() throws Exception {
  new RecoveryMail(provider,"local","",directory.toString(),"127.0.0.1").send("demo@campus.test","http://127.0.0.1:8080/reset-password?token=test");
  try(var files=Files.list(directory)) {var file=files.findFirst().orElseThrow();assertTrue(Files.readString(file).contains("token=test"));assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));}
  verifyNoInteractions(provider);
 }
 @Test void smtpUsesConfiguredRecipientAndSender(){var sender=mock(JavaMailSender.class);when(provider.getIfAvailable()).thenReturn(sender);new RecoveryMail(provider,"smtp","sender@example.com","unused","127.0.0.1").send("recipient@example.com","https://example.com/reset");var capture=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(capture.capture());assertEquals("sender@example.com",capture.getValue().getFrom());assertEquals("recipient@example.com",capture.getValue().getTo()[0]);assertTrue(capture.getValue().getText().contains("https://example.com/reset"));}
 @Test void disabledModeCannotDeliver(){assertThrows(IllegalStateException.class,()->new RecoveryMail(provider,"disabled","","unused","127.0.0.1").send("a@b.test","link"));}
 @Test void localModeRejectsPublicBinding(){assertThrows(IllegalArgumentException.class,()->new RecoveryMail(provider,"local","","unused","0.0.0.0"));}
}
