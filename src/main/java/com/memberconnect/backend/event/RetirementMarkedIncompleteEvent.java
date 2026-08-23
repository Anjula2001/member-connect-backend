package com.memberconnect.backend.event;

/**
 * Published by RetirementService once a retirement request has been saved with
 * status INCOMPLETE (MMT14).
 *
 * The event carries only the identifiers and the reason - never the member's
 * contact details. Recipient resolution happens later, in NotificationService,
 * so that the contact information is read from the Member entity at send time
 * rather than being copied around.
 *
 * Consumed by RetirementNotificationListener after the transaction commits.
 */
public record RetirementMarkedIncompleteEvent(
        String memberId,
        String requestNo,
        String reason
) {
}
