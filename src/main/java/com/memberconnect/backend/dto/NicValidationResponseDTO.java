package com.memberconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NicValidationResponseDTO {

    private boolean valid;

    /** True when the NIC clashes with an active member/application — blocks saving. */
    private boolean duplicate;

    private String message;

    /**
     * True when the NIC belongs to a previously TERMINATED member. This does NOT block
     * the application: the user is shown the previous membership details and may
     * continue, and the resulting application is flagged as a Rejoin.
     */
    private boolean rejoin;

    // Previous membership details, populated only when rejoin == true.
    private String previousMemberId;
    private String previousMemberName;
    private LocalDate membershipStartDate;
    private LocalDate terminatedDate;
    private String terminationReason;
    private String terminationComments;

    /** Convenience constructor for the plain (non-rejoin) outcomes. */
    public NicValidationResponseDTO(boolean valid, boolean duplicate, String message) {
        this.valid = valid;
        this.duplicate = duplicate;
        this.message = message;
    }
}
