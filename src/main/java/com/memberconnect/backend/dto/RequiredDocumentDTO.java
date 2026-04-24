package com.memberconnect.backend.dto;

public class RequiredDocumentDTO {
    private Long documentTypeId;
    private String documentCode;
    private String documentName;
    private boolean mandatory;
    private boolean uploaded;

    public Long getDocumentTypeId() { return documentTypeId; }
    public void setDocumentTypeId(Long documentTypeId) { this.documentTypeId = documentTypeId; }
    public String getDocumentCode() { return documentCode; }
    public void setDocumentCode(String documentCode) { this.documentCode = documentCode; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public boolean isUploaded() { return uploaded; }
    public void setUploaded(boolean uploaded) { this.uploaded = uploaded; }
}