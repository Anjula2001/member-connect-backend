package com.memberconnect.backend.event;

/**
 * Published by TerminationService once a termination request has been saved with
 * status INCOMPLETE.
 *
 * The event carries only the identifiers and the reason - never the member's
 * contact details. Recipient resolution happens later, in NotificationService,
 * so that the contact information is read from the Member entity at send time
 * rather than being copied around.
 *
 * Consumed by TerminationNotificationListener after the transaction commits.
 */
public record TerminationMarkedIncompleteEvent(
        String memberId,
        String requestNo,
        String reason
) {
}
