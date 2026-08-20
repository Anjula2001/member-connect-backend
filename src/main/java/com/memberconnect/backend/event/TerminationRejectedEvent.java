package com.memberconnect.backend.event;

/**
 * Published by TerminationService once a termination request has been rejected
 * by the board.
 *
 * SRS MMT09 requires the member to be told by SMS and email, with the reason,
 * and the member's profile to be back at Active - both of which are already
 * durable by the time this is consumed after commit.
 */
public record TerminationRejectedEvent(String memberId, String requestNo, String reason) {
}
