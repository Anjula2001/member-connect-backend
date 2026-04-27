package com.memberconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "required_document_types")
public class RequiredDocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="request_type", nullable = false)
    private String requestType;

    @Column(name="document_type", nullable = false)
    private String documentType;

    @Column(name="display_name", nullable = false)
    private String displayName;

    @Column(name="mandatory", nullable = false)
    private Boolean mandatory;

    public Long getId() { 
        return id; 
    }

    public String getRequestType() { 
        return requestType; 
    }

    public void setRequestType(String requestType) { 
        this.requestType = requestType; 
    }

    public String getDocumentType() { 
        return documentType; 
    }

    public void setDocumentType(String documentType) { 
        this.documentType = documentType;
    }

    public String getDisplayName() { 
        return displayName; 
    }

    public void setDisplayName(String displayName) { 
        this.displayName = displayName; 
    }

    public Boolean getMandatory() { 
        return mandatory; 
    }
    public void setMandatory(Boolean mandatory) {
        this.mandatory = mandatory;
    }
}