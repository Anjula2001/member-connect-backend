package com.memberconnect.backend.dto;

public class UploadedDocumentUploadDto {
    private Long id;
    private Long requestId;
    private Long requiredDocumentId;
    private String message;
    private boolean success;

    public UploadedDocumentUploadDto() {}

    public UploadedDocumentUploadDto(Long id, Long requestId, Long requiredDocumentId, String message, boolean success) {
        this.id = id;
        this.requestId = requestId;
        this.requiredDocumentId = requiredDocumentId;
        this.message = message;
        this.success = success;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getRequiredDocumentId() { return requiredDocumentId; }
    public void setRequiredDocumentId(Long requiredDocumentId) { this.requiredDocumentId = requiredDocumentId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
