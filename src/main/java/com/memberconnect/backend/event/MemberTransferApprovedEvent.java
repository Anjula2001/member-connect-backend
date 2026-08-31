package com.memberconnect.backend.event;

/**
 * Published by MemberTransferService once an approved transfer has moved the member to
 * a different District, and the transaction has committed.
 *
 * Consumed by FinanceMemberTransferListener and LoanMemberTransferListener, which ask
 * the Finance and Loan Modules to move the member's accounts and loans to the new
 * District Office (SRS MMC30).
 *
 * Published only when the District actually changed. A transfer that keeps the member
 * in the same District - including one where "Keep Current District" was ticked, which
 * resolves to the same district on the request - moves no records and raises no event.
 *
 * Carries the two districts as well as the identifiers, because unlike the termination
 * events the "from" value is gone by the time a listener runs: the member row has
 * already been overwritten with the new district.
 */
public record MemberTransferApprovedEvent(
        String memberId,
        String requestNo,
        String fromDistrict,
        String toDistrict
) {
}
