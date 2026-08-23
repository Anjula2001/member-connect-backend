package com.memberconnect.backend.event;

/**
 * Published by Grade5ScholarshipApprovalListService once a Grade 5 scholarship request
 * has been saved with status REJECTED by the Board (MMS11 / MMS18).
 *
 * Carries the student name for the same reason as the incomplete event: a member may
 * have more than one child with a request in flight.
 *
 * Consumed by Grade5NotificationListener after the transaction commits.
 */
public record Grade5RejectedEvent(
        String memberId,
        String requestNo,
        String studentName,
        String reason
) {
}
