package com.memberconnect.backend.dto;

import java.time.LocalDateTime;

public class UploadedDocumentDisplayDto {
    private Long id;
    private Long requiredDocumentId;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;

    public UploadedDocumentDisplayDto() {}

    public UploadedDocumentDisplayDto(Long id, Long requiredDocumentId, String fileName, String fileType, LocalDateTime uploadedAt) {
        this.id = id;
        this.requiredDocumentId = requiredDocumentId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequiredDocumentId() { return requiredDocumentId; }
    public void setRequiredDocumentId(Long requiredDocumentId) { this.requiredDocumentId = requiredDocumentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

}
