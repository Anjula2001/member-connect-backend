package com.memberconnect.backend.dto;

public class RequiredDocumentDTO {
    private Long id;
    private String documentName;
    private boolean mandatory;
    private boolean uploaded;

    public RequiredDocumentDTO(Long id, String documentName, boolean mandatory, boolean uploaded) {
        this.id = id;
        this.documentName = documentName;
        this.mandatory = mandatory;
        this.uploaded = uploaded;
    }

    public Long getId() {
        return id;
    }

    public String getDocumentName() {
        return documentName;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isUploaded() {
        return uploaded;
    }
}