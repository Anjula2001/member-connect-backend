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

    /** Who decided the request, and when (MMC17). Set by the server on approve/reject. */
    private String processedBy;
    private java.time.LocalDateTime processedAt;

    // --- Member Details block (MMC14): resolved from the member, not stored. ---
    private String memberFullName;
    private String memberNameWithInitials;
    private String memberNic;

    /**
     * One row per editable account, each with the amount that stood when the request
     * was raised and the amount being asked for.
     */
    private java.util.List<RemittanceChangeLineDTO> lines = new java.util.ArrayList<>();

    /** @deprecated flattened total; replaced by per-account line items. */
    @Deprecated
    private String newRemittanceAmount;

    private String newRemittanceCurrency;

    /** @deprecated only ever held the first selected account. */
    @Deprecated
    private String remittanceAccountType;
}
