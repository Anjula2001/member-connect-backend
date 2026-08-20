package com.memberconnect.backend.event;

/**
 * A Member Death Record was approved. The member is MEMBER_DEATH_APPROVED, not
 * yet DECEASED - this event is what hands the record to the Finance Module
 * (MMT25) to close the accounts.
 */
public record MemberDeathApprovedEvent(String memberId, String recordNo) {
}
