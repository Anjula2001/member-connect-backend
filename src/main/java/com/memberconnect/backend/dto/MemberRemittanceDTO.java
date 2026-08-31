package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.RemittanceAccountCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MemberRemittanceDTO {
    private Long id;
    private RemittanceAccountCode accountCode;
    /** Display label from the Remittance Master. */
    private String accountName;
    private BigDecimal amount;
    private LocalDate effectiveFrom;
    /** Master rules, so the UI can lock/validate without a second call. */
    private BigDecimal fixedAmount;
    private BigDecimal minimumAmount;
}
