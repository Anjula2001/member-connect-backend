package com.memberconnect.backend.event;

/**
 * Published when the identification process flags a member as dormant (MMD10).
 *
 * The member is warned while they can still do something about it: a single
 * transaction before the next board meeting clears the flag. Without this the
 * first they hear of it is the inactivation notice, by which point reversing it
 * needs Head Office.
 */
public record DormantSelectedEvent(String memberId, int dormantPeriodMonths) {
}
