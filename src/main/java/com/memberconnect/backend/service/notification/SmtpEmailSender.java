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

    /**
     * Hands the message to Gmail on a notification thread, not the caller's.
     *
     * The caller - NotificationService, reached from an AFTER_COMMIT hook - is finished
     * with the member record by this point and passes plain strings, so nothing here
     * touches a JPA entity and the missing open-in-view session on this thread does not
     * matter. What it buys is that the HTTP request completes as soon as the business
     * record is committed: an SMTP conversation that takes ten seconds no longer shows
     * up in the browser as a failed status change.
     *
     * Because the method is @Async and void, an exception thrown here cannot reach the
     * caller's try/catch - it would vanish into the executor. It is therefore caught and
     * logged in full right here, which is also the only place that can honestly report
     * whether the message actually went out.
     */
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
