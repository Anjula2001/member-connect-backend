package com.memberconnect.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UploadDocumentResponseDTO {
    private Long id;
    private String documentId;
    private Long applicationId;
    private String documentType;
    private String fileName;
    private String fileType;
    private String storagePath;
    private Long fileSize;
    private LocalDateTime uploadedAt;
}
