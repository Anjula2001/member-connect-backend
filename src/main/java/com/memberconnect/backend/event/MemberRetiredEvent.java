package com.memberconnect.backend.event;

/**
 * Published by RetirementService once the Finance Module hand-off has completed and
 * the member has moved to RETIRED (MMT17).
 *
 * Deliberately NOT published at approval. Approval only moves the member to
 * RETIREMENT_APPROVED; telling somebody their membership has ended while Finance still
 * has their accounts open would be untrue - the same distinction TerminationService
 * draws between board approval and MMT11.
 *
 * Consumed by RetirementNotificationListener after the transaction commits.
 */
public record MemberRetiredEvent(
        String memberId,
        String requestNo
) {
}
