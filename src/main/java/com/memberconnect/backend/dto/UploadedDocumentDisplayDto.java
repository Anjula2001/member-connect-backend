package com.memberconnect.backend.dto;

import java.time.LocalDateTime;

public class UploadedDocumentDisplayDto {
    private Long id;
    private String requestId;
    private Long requiredDocumentId;
    private String fileName;
    private String filePath;
    private String fileType;
    private LocalDateTime uploadedAt;

    public UploadedDocumentDisplayDto() {}

    public UploadedDocumentDisplayDto(Long id, String requestId, Long requiredDocumentId, String fileName, String filePath, String fileType, LocalDateTime uploadedAt) {
        this.id = id;
        this.requestId = requestId;
        this.requiredDocumentId = requiredDocumentId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileType = fileType;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getRequiredDocumentId() { return requiredDocumentId; }
    public void setRequiredDocumentId(Long requiredDocumentId) { this.requiredDocumentId = requiredDocumentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

}
