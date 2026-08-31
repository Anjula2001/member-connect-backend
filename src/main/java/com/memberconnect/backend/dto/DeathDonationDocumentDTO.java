package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeathDonationDocumentDTO {
    private Long id;
    private String requestNo;

    /** The stable code an upload is filed under, e.g. DEATH_CERTIFICATE. */
    private String documentType;

    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;

    /**
     * Only set on the Required Documents listing: the display name and whether
     * the document must be present before a request may be submitted, both taken
     * from the Supporting Documents master rather than a hardcoded list.
     */
    private String documentName;
    private boolean mandatory;
}
