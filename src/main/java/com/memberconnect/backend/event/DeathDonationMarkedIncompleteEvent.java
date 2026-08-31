package com.memberconnect.backend.event;

/**
 * A Death Donation Request was marked incomplete (SRS MMD01, p.15): "the request
 * status will be updated as incomplete and send an SMS and Email to the Member
 * indicating that the request is incomplete with the reason".
 *
 * Carries identifiers and the reason only - never contact details. The listener
 * resolves the member at delivery time, so a stale number cannot be captured
 * here and delivered later.
 */
public record DeathDonationMarkedIncompleteEvent(String memberId, String requestNo, String reason) {
}
