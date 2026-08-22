package com.memberconnect.backend.dto;

import lombok.Data;

/**
 * One board decision against one termination request inside a Termination
 * Approval List (SRS MMT09).
 *
 * The board marks each request in the list individually - the list as a whole
 * carries no single verdict - so the decision has to travel per request rather
 * than being inferred from the others. A blank rejectReason is only valid when
 * the decision is Approve; the SRS makes the reason mandatory for every
 * rejected request before the list may be processed.
 */
@Data
public class TerminationRequestDecisionDTO {

    private String requestNo;

    /** "Approve" or "Reject", case-insensitive. */
    private String decision;

    /** Mandatory when decision is Reject, ignored otherwise. */
    private String rejectReason;
}
