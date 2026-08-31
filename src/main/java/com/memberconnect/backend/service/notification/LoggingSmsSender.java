package com.memberconnect.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only SmsSender that exists today: writes the message to the application log.
 * No SMS gateway is integrated, so this application cannot send a real SMS.
 *
 * Unlike {@link LoggingEmailSender} this bean is not gated on
 * {@code notification.sms.enabled}, because there is no real implementation for the
 * property to switch to - gating it would leave the context with no SmsSender at all
 * and fail startup. Instead the flag is read here: setting it to true logs a warning
 * that real delivery was asked for and did not happen, which is far easier to notice
 * than a silently simulated send.
 *
 * When a real provider is added (for example HttpSmsSender), it should be annotated
 * {@code @ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true")}
 * and this class should receive the matching inverse condition, mirroring the email pair.
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    private final boolean realSendingRequested;

    public LoggingSmsSender(
            @Value("${notification.sms.enabled:false}") boolean realSendingRequested
    ) {
        this.realSendingRequested = realSendingRequested;
    }

    @Override
    public void send(String toMobile, String message) {
        if (realSendingRequested) {
            log.warn(
                    "notification.sms.enabled=true but no real SMS provider is integrated. "
                            + "The message below was NOT delivered."
            );
        }

        // The recipient is masked; the message is safe to log because
        // NotificationService builds it from the request number and the
        // incomplete reason only.
        log.info(
                "[SMS - NOT SENT, logging sender active] to={} message={}",
                ContactMasking.maskMobile(toMobile),
                message
        );
    }
}
