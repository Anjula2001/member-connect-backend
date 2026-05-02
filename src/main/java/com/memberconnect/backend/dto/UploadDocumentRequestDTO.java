package com.memberconnect.backend.dto;

import lombok.Data;

@Data
public class UploadDocumentRequestDTO {
    private Long applicationId;
    private String documentType;
    private String fileName;
    private String fileType;
    private String storagePath;
    private Long fileSize;
}
