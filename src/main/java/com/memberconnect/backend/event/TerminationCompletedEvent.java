package com.memberconnect.backend.event;

/**
 * Published once the Finance Module reports that it has finished closing a
 * terminated member's accounts and the member has moved to TERMINATED.
 *
 * SRS MMT11: this is the point at which the member is told their membership is
 * now terminated - not at board approval, which only stops the remittance.
 */
public record TerminationCompletedEvent(String memberId, String requestNo) {
}
