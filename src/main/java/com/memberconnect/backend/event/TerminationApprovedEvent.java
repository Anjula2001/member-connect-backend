package com.memberconnect.backend.event;

/**
 * Published by TerminationService once a termination request has been approved
 * by the board and the transaction has committed.
 *
 * Consumed by FinanceTerminationListener, which hands the member over to the
 * Finance Module for account closing (SRS MMT11). Like the other termination
 * events this carries only identifiers - the full handoff payload is read from
 * the repository at send time rather than being copied through the event.
 */
public record TerminationApprovedEvent(String memberId, String requestNo) {
}
