package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real email transport, active only when {@code notification.email.enabled=true}.
 * With the shipped default of false this bean is never created and
 * {@link LoggingEmailSender} is used instead, so no real email leaves a
 * development machine unless somebody deliberately opts in.
 *
 * SMTP host, port, username and password come from Spring's own
 * {@code spring.mail.*} properties, which application.properties resolves from
 * environment variables following the existing EnvConfig/.env pattern. No
 * credential is read or held by this class.
 */
@Component
@ConditionalOnProperty(
        name = "notification.email.enabled",
        havingValue = "true"
)
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${notification.email.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);

        // Recipient masked: a delivery confirmation must not turn the log into a
        // list of member email addresses.
        log.info("[EMAIL - SENT via SMTP] to={} subject={}", ContactMasking.maskEmail(to), subject);
    }
}
