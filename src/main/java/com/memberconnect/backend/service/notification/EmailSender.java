package com.memberconnect.backend.service.notification;

/**
 * Transport-level abstraction for sending a single plain-text email.
 *
 * Exactly one implementation is active at a time, selected by the
 * {@code notification.email.enabled} property:
 *
 * <ul>
 *   <li>{@code false} (default) - {@link LoggingEmailSender}, which only logs.</li>
 *   <li>{@code true} - {@link SmtpEmailSender}, which contacts a real SMTP server.</li>
 * </ul>
 *
 * Implementations may throw; NotificationService isolates each channel so that a
 * failure here can never affect the SMS channel or the termination request itself.
 */
public interface EmailSender {

    /**
     * @param to      recipient email address (never logged in full)
     * @param subject message subject line
     * @param body    plain-text message body
     */
    void send(String to, String subject, String body);
}
