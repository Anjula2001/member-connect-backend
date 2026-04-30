package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.*;

@Table(name = "NameChangeRequestsTable")
@Entity
public class NameChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NameChangeRequestID")
    private Integer nameChangeRequestID;

    @Column(name = "title")
    private String newTitle;

    @Column(name = "fullname")
    private String newFullName;

    @Column(name = "nameAszPayroll")
    private String newNameAsInPayroll;

    @Column(name = "Name with initials")
    private String newNameWithInitials;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    // --- 1. No-Args Constructor (Required by JPA) ---
    public NameChangeRequest() {
    }

    // --- 2. All-Args Constructor ---
    public NameChangeRequest(Integer nameChangeRequestID, String newTitle, String newFullName,
                             String newNameAsInPayroll, String newNameWithInitials,
                             ApplicationStatus newStatus) {
        this.nameChangeRequestID = nameChangeRequestID;
        this.newTitle = newTitle;
        this.newFullName = newFullName;
        this.newNameAsInPayroll = newNameAsInPayroll;
        this.newNameWithInitials = newNameWithInitials;
        this.newStatus = newStatus;
    }

    // --- 3. Getters and Setters ---

    public Integer getNameChangeRequestID() {
        return nameChangeRequestID;
    }

    public void setNameChangeRequestID(Integer nameChangeRequestID) {
        this.nameChangeRequestID = nameChangeRequestID;
    }

    public String getNewTitle() {
        return newTitle;
    }

    public void setNewTitle(String newTitle) {
        this.newTitle = newTitle;
    }

    public String getNewFullName() {
        return newFullName;
    }

    public void setNewFullName(String newFullName) {
        this.newFullName = newFullName;
    }

    public String getNewNameAsInPayroll() {
        return newNameAsInPayroll;
    }

    public void setNewNameAsInPayroll(String newNameAsInPayroll) {
        this.newNameAsInPayroll = newNameAsInPayroll;
    }

    public String getNewNameWithInitials() {
        return newNameWithInitials;
    }

    public void setNewNameWithInitials(String newNameWithInitials) {
        this.newNameWithInitials = newNameWithInitials;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(ApplicationStatus newStatus) {
        this.newStatus = newStatus;
    }
}
