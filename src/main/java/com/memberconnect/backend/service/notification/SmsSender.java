package com.memberconnect.backend.service.notification;

/**
 * Transport-level abstraction for sending a single SMS message.
 *
 * Only {@link LoggingSmsSender} exists today - no real SMS gateway is integrated,
 * so no real SMS can be sent by this application. The interface exists so that a
 * real provider (for example an HttpSmsSender calling a gateway REST API) can be
 * added later without touching NotificationService or TerminationService.
 *
 * Implementations may throw; NotificationService isolates each channel so that a
 * failure here can never affect the email channel or the termination request itself.
 */
public interface SmsSender {

    /**
     * @param toMobile recipient mobile number (never logged in full)
     * @param message  plain-text message body, kept short enough for SMS
     */
    void send(String toMobile, String message);
}
