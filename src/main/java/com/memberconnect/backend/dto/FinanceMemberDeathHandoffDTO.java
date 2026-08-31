package com.memberconnect.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload handed to the Finance Module when a Member Death Record is approved
 * (SRS MMT25), so it can close the accounts and disburse the funds.
 *
 * Carries the settled donation split rather than the inputs behind it: Finance
 * pays what the approval levels agreed, it does not recompute the entitlement.
 */
@Data
public class FinanceMemberDeathHandoffDTO {

    private String recordNo;
    private String memberId;
    private String memberName;
    private String nic;
    private LocalDate deceasedDate;
    private String causeOfDeath;

    private String nomineeFullName;
    private String nomineeBank;
    private String nomineeBranch;
    private String nomineeAccountNo;

    /** Paid to the nominee. */
    private BigDecimal disburseDonationAmount;

    /** Retained in the Special Fixed Account for Funerals, where one exists. */
    private BigDecimal creditedToSpecialFixedAccount;
    private String funeralAccountNo;

    private List<MemberDeathMinorDisbursementDTO> minorDisbursements = new ArrayList<>();
}
