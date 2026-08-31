package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.config.AsyncNotificationConfig;


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
    @Async(AsyncNotificationConfig.NOTIFICATION_EXECUTOR)
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            // Recipient masked: a delivery confirmation must not turn the log into a
            // list of member email addresses.
            log.info("[EMAIL - SENT via SMTP] to={} subject={}", ContactMasking.maskEmail(to), subject);
        } catch (Exception e) {
            log.error(
                    "[EMAIL - FAILED via SMTP] to={} subject={} cause={}",
                    ContactMasking.maskEmail(to), subject, e.toString(), e
            );
        }
    }
}
