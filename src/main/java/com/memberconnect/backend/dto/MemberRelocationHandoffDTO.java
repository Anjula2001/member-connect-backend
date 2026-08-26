package com.memberconnect.backend.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * The payload sent to the Finance and Loan Modules when an approved transfer moves a
 * member to a different District (SRS MMC30).
 *
 * Both modules are asked to do the same thing - re-file this member's records under the
 * new District Office - so both receive the same payload rather than two near-identical
 * DTOs. It carries the district being left as well as the one being joined, so the
 * receiving module can verify it is moving the records it expects to.
 *
 * Fire and forget: the SRS describes a message being sent, not a conversation, so there
 * is no callback endpoint for either module to reply on.
 */
@Data
public class MemberRelocationHandoffDTO {

    private String requestNo;
    private String memberId;
    private String memberName;
    private String nic;

    /** The District the member is leaving. Null where none was recorded. */
    private String fromDistrict;

    /** The District the member now belongs to. */
    private String toDistrict;

    /** The new working location, for the receiving module's own records. */
    private String newWorkingLocation;

    /** When the transfer was approved. */
    private LocalDate approvedOn;
}
