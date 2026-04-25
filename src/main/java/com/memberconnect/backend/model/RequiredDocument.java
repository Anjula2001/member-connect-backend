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

    public String getDocumentName() {
        return documentName;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}