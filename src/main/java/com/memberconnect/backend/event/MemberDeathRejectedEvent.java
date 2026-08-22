package com.memberconnect.backend.event;

/**
 * A Member Death Record was rejected at one of the three approval levels
 * (MMT22 / MMT23 / MMT24). The member profile has already gone back to Active.
 *
 * @param level human-readable level the rejection came from, for the message body
 */
public record MemberDeathRejectedEvent(String memberId, String recordNo, String reason, String level) {
}
