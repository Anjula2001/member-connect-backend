package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * The payload handed to the Finance Module when the Board approves a dormant
 * membership for inactivation (SRS 4.2.7).
 *
 * Deliberately smaller than FinanceTerminationHandoffDTO, and the difference is
 * the point. A termination asks Finance to CLOSE accounts and disburse balances,
 * so it must carry disbursement instructions and MemberConnect waits for the
 * reply. Dormancy asks Finance only to FLAG the accounts - no money moves - so
 * this carries identification and the board's authority for the change, and
 * nothing comes back.
 */
@Data
public class FinanceDormantHandoffDTO {

    private String memberId;
    private String memberName;
    private String nic;

    /** The Inactivation Approval List that authorised this. */
    private String listId;

    /** The date the Board actually sat, which is the authority for the change. */
    private LocalDate boardMeetingDate;

    /** When MemberConnect applied the inactivation. */
    private LocalDate inactivatedOn;
}
