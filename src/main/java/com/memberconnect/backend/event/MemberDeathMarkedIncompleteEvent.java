package com.memberconnect.backend.event;

/**
 * A Member Death Record was marked incomplete (MMT18). The nominee is told why.
 *
 * Carries identifiers and the reason only - never contact details. The listener
 * resolves the nominee at delivery time, so a stale phone number cannot be
 * captured here and delivered later.
 */
public record MemberDeathMarkedIncompleteEvent(String memberId, String recordNo, String reason) {
}
