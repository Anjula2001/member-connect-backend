package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default EmailSender: writes the message to the application log instead of
 * contacting an SMTP server. Active whenever {@code notification.email.enabled}
 * is absent or false, which is the shipped default - so a fresh checkout can
 * never send a real email to a real member.
 *
 * {@link SmtpEmailSender} replaces this bean when the property is set to true.
 */
@Component
@ConditionalOnProperty(
        name = "notification.email.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        // The recipient is masked; the subject and body are safe to log because
        // NotificationService builds them from the request number and the
        // incomplete reason only.
        log.info(
                "[EMAIL - NOT SENT, logging sender active] to={} subject={}\n{}",
                ContactMasking.maskEmail(to),
                subject,
                body
        );
    }
}
