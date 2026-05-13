package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.DeathDonationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO returned to the frontend after any create / update / fetch operation.
 */
@Getter
@Setter
public class DeathDonationResponseDTO {

    private Long id;
    private String requestId;

    // Requesting member
    private Long memberId;
    private String memberName;

    // Request details
    private String relationshipToDeceased;
    private LocalDate requestedDate;
    private Boolean isDeceasedMember;
    private String deceasedMemberId;
    private String deceasedName;
    private String maidenName;
    private LocalDate deceasedDate;
    private String deathCertificateNumber;
    private String placeOfWork;
    private String concernsIdentified;

    // Status
    private DeathDonationStatus status;
    private String incompleteReason;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Child collections
    private List<RelativeResponseDTO> relatives;
    private List<DocumentResponseDTO> documents;

    // ── Nested response DTOs ──────────────────────────────────────────────────

    @Getter
    @Setter
    public static class RelativeResponseDTO {
        private Long id;
        private String memberId;
        private String relationshipToDeceased;
        private Boolean isAuto;
    }

    @Getter
    @Setter
    public static class DocumentResponseDTO {
        private Long id;
        private String documentType;
        private String fileName;
        private String mimeType;
        private Boolean mandatory;
        private LocalDateTime uploadedAt;
    }
}
