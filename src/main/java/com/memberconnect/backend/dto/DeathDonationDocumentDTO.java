package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeathDonationDocumentDTO {
    private Long id;
    private String requestNo;
    private String documentType;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;
}
