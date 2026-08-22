package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The payload handed to the Finance Module when the board approves a
 * termination (SRS MMT11).
 *
 * It carries what Finance needs to close the accounts and disburse the
 * balances: who the member is, when the termination takes effect, and where any
 * minor savings balances should be paid. Finance replies asynchronously by
 * calling back to PATCH /api/finance/terminations/{requestNo}/complete.
 */
@Data
public class FinanceTerminationHandoffDTO {

    private String requestNo;
    private String memberId;
    private String memberName;
    private String nic;
    private LocalDate effectiveDate;
    private String terminationReason;

    /** Where each minor savings account balance is to be disbursed. */
    private List<TerminationMinorDisbursementDTO> minorDisbursements = new ArrayList<>();
}
