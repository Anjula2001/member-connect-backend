package com.memberconnect.backend.dto;

import lombok.Data;

/**
 * An approver's decision on a profile change request (Requirement 02, MMC04 / MMC17).
 *
 * The reject reason is mandatory when decision is REJECT — the SRS shows a popup for
 * it — and is ignored on approve.
 */
@Data
public class ProfileChangeDecisionDTO {

    public enum Decision {
        APPROVE,
        REJECT
    }

    private Decision decision;

    private String rejectReason;
}
