package com.memberconnect.backend.event;

/**
 * Published by Grade5ScholarshipService once a Grade 5 scholarship request has been
 * saved with status INCOMPLETE (MMS04).
 *
 * Carries the student name as well as the identifiers because a member may have more
 * than one child with a request in flight - "your scholarship request is incomplete"
 * with no name on it would leave them guessing which one.
 *
 * Contact details are deliberately absent: NotificationService resolves the recipient
 * from the Member entity at send time rather than having it copied around.
 *
 * Consumed by Grade5NotificationListener after the transaction commits.
 */
public record Grade5MarkedIncompleteEvent(
        String memberId,
        String requestNo,
        String studentName,
        String reason
) {
}
