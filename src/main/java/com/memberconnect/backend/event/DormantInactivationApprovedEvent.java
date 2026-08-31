package com.memberconnect.backend.event;

/**
 * Published by DormantMembershipService once the board has approved a member for
 * inactivation and the transaction has committed (SRS MMD17).
 *
 * Two consumers: DormantNotificationListener, which tells the member their
 * membership is now Inactive (Dormant), and FinanceDormantListener, which asks
 * the Finance Module to flag the accounts (SRS 4.2.7).
 *
 * Like the other module events this carries only identifiers - the full handoff
 * payload is read from the repository at send time rather than copied through.
 */
public record DormantInactivationApprovedEvent(String memberId, String listId) {
}
