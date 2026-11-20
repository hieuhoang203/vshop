package vn.vshop.modules.platform.gateway;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP delivery, so any provider that offers SMTP works (Resend, Brevo, SES, a company server) by
 * configuration alone. Credentials come from the environment; nothing is hard-coded. Connection and
 * read timeouts keep a stuck mail server from holding a worker thread.
 */
@Component
public class SmtpEmailSender implements EmailSender {
    private final String from;
    private final String host;
    private final JavaMailSenderImpl mail = new JavaMailSenderImpl();

    SmtpEmailSender(
            @Value("${vshop.email.smtp.host:}") String host,
            @Value("${vshop.email.smtp.port:587}") int port,
            @Value("${vshop.email.smtp.username:}") String username,
            @Value("${vshop.email.smtp.password:}") String password,
            @Value("${vshop.email.smtp.starttls:true}") boolean startTls,
            @Value("${vshop.email.from:}") String from) {
        this.host = host;
        this.from = from;
        mail.setHost(host);
        mail.setPort(port);
        mail.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties p = mail.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        if (!username.isBlank()) {
            mail.setUsername(username);
            mail.setPassword(password);
            p.put("mail.smtp.auth", "true");
        }
        p.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
    }

    @Override
    public boolean configured() {
        return !host.isBlank() && !from.isBlank();
    }

    @Override
    public void send(Message message) {
        try {
            MimeMessage mime = mail.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mail.send(mime);
        } catch (MessagingException e) {
            throw new IllegalStateException("Cannot build the email", e);
        }
    }
}
