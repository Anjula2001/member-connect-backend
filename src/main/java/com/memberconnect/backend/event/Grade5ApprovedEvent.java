package com.memberconnect.backend.event;

/**
 * Published by Grade5ScholarshipApprovalListService once a Grade 5 scholarship request
 * has been saved with status APPROVED by the Board (MMS11 / MMS18).
 *
 * Consumed by Grade5NotificationListener after the transaction commits.
 */
public record Grade5ApprovedEvent(
        String memberId,
        String requestNo,
        String studentName
) {
}
