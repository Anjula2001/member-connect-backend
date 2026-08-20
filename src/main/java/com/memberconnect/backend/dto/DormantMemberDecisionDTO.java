package com.memberconnect.backend.dto;

import lombok.Data;

/**
 * One board decision against one member inside an Inactivation Approval List
 * (SRS MMD17).
 *
 * The board marks each dormant member in the list individually - the list as a
 * whole carries no single verdict - so the decision has to travel per member
 * rather than being inferred from the others. A blank rejectReason is only valid
 * when the decision is Approve; the SRS makes the reason mandatory for every
 * rejected record before the user may proceed with the approval process.
 */
@Data
public class DormantMemberDecisionDTO {

    private String memberId;

    /** "Approve" or "Reject", case-insensitive. */
    private String decision;

    /** Mandatory when decision is Reject, ignored otherwise. */
    private String rejectReason;
}
