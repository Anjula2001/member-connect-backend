package com.memberconnect.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "required_document")
public class RequiredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicationType;

    private String documentName;

    private boolean mandatory;

    public Long getId() {
        return id;
    }

    public String getApplicationType() {
        return applicationType;
    }

    // Setters exist so the master can be seeded (TerminationDocumentSeeder). Rows are
    // otherwise treated as read-only reference data: nothing in the request flows
    // mutates a RequiredDocument, they only read it and store its id against an upload.
    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
}