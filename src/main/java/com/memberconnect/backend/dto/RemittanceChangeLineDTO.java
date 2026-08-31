package com.memberconnect.backend.dto;

import java.math.BigDecimal;

import com.memberconnect.backend.enums.RemittanceAccountCode;

import lombok.Data;

/**
 * One account's row on the Remittance Amount Change Request screen (MMC14).
 *
 * accountName, minimumAmount and mandatory come from the Remittance Master rather than
 * the request, so the screen can label the row and show the limit it will be held to
 * without a second lookup. Only accountCode and newAmount are read back off the client;
 * everything else here is server-supplied and overwritten on submit.
 */
@Data
public class RemittanceChangeLineDTO {

    private RemittanceAccountCode accountCode;

    /** From the Remittance Master, for display. */
    private String accountName;

    /** The member's amount when the request was raised. Read-only to clients. */
    private BigDecimal oldAmount;

    /** The requested amount. Validated against minimumAmount on submit. */
    private BigDecimal newAmount;

    /** The configured floor for this account, or null when none is set. */
    private BigDecimal minimumAmount;

    private Boolean mandatory;
}
