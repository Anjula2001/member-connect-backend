package com.memberconnect.backend.dto;

public class UploadedDocumentUploadDto {
    private Long id;
    private String requestId;
    private Long requiredDocumentId;
    private String message;
    private boolean success;

    public UploadedDocumentUploadDto() {}

    public UploadedDocumentUploadDto(Long id, String requestId, Long requiredDocumentId, String message, boolean success) {
        this.id = id;
        this.requestId = requestId;
        this.requiredDocumentId = requiredDocumentId;
        this.message = message;
        this.success = success;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getRequiredDocumentId() { return requiredDocumentId; }
    public void setRequiredDocumentId(Long requiredDocumentId) { this.requiredDocumentId = requiredDocumentId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
