package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.ApplicationStatus;
import lombok.Data;

/**
 * Remittance Amount Change Request (Requirement 02, MMC14-MMC17).
 *
 * newRemittanceAmount / remittanceAccountType are retained only until the per-account
 * line items land. MMC14 requires one amount per remittance account, each validated
 * against that account's configured minimum on submit; a single amount and a single
 * account type cannot carry that, and the screen was summing every row into the
 * amount and keeping only the first account type.
 */
@Data
public class RemittanceAmountChangeDTO {

    private Integer id;

    /** Generated on submit; null on a new request, which the screen shows as "NEW". */
    private String requestNo;

    private String memberId;

    private ApplicationStatus status;

    private java.time.LocalDate requestedDate;

    private String rejectReason;

    private String submissionLocation;

    /** @deprecated flattened total; replaced by per-account line items. */
    @Deprecated
    private String newRemittanceAmount;

    private String newRemittanceCurrency;

    /** @deprecated only ever held the first selected account. */
    @Deprecated
    private String remittanceAccountType;
}
