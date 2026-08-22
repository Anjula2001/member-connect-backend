package com.memberconnect.backend.event;

/**
 * The Finance Module confirmed every account is closed and the member is now
 * DECEASED (MMT25). The nominee is told, with the disbursement details.
 */
public record MemberDeathCompletedEvent(String memberId, String recordNo) {
}
