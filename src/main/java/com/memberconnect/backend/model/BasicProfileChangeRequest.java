package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class BasicProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    private LocalDate newDateOfBirth;
    private String newNIC;
    private String newGender;
    private String newPreferredLanguage;
    private String newPermanentPrivateAddress;
    private String newPrivateTelephone;
    private String newMobileNumber;
    private String newEmailAddress;
    private String newDesignationId;
    private String newNatureOfOccupation;

    // --- Constructors ---

    // Default constructor (Required by JPA)
    public BasicProfileChangeRequest() {
    }

    // All-args constructor
    public BasicProfileChangeRequest(Integer id, LocalDate newDateOfBirth, String newNIC, String newGender,
                                     String newPreferredLanguage, String newPermanentPrivateAddress,
                                     String newPrivateTelephone, String newMobileNumber, String newEmailAddress,
                                     String newDesignationId, String newNatureOfOccupation,ApplicationStatus newStatus) {
        this.id = id;
        this.newStatus = newStatus;
        this.newDateOfBirth = newDateOfBirth;
        this.newNIC = newNIC;
        this.newGender = newGender;
        this.newPreferredLanguage = newPreferredLanguage;
        this.newPermanentPrivateAddress = newPermanentPrivateAddress;
        this.newPrivateTelephone = newPrivateTelephone;
        this.newMobileNumber = newMobileNumber;
        this.newEmailAddress = newEmailAddress;
        this.newDesignationId = newDesignationId;
        this.newNatureOfOccupation = newNatureOfOccupation;
    }

    // --- Getters and Setters ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getNewDateOfBirth() {
        return newDateOfBirth;
    }

    public void setNewDateOfBirth(LocalDate newDateOfBirth) {
        this.newDateOfBirth = newDateOfBirth;
    }

    public String getNewNIC() {
        return newNIC;
    }

    public void setNewNIC(String newNIC) {
        this.newNIC = newNIC;
    }

    public String getNewGender() {
        return newGender;
    }

    public void setNewGender(String newGender) {
        this.newGender = newGender;
    }

    public String getNewPreferredLanguage() {
        return newPreferredLanguage;
    }

    public void setNewPreferredLanguage(String newPreferredLanguage) {
        this.newPreferredLanguage = newPreferredLanguage;
    }

    public String getNewPermanentPrivateAddress() {
        return newPermanentPrivateAddress;
    }

    public void setNewPermanentPrivateAddress(String newPermanentPrivateAddress) {
        this.newPermanentPrivateAddress = newPermanentPrivateAddress;
    }

    public String getNewPrivateTelephone() {
        return newPrivateTelephone;
    }

    public void setNewPrivateTelephone(String newPrivateTelephone) {
        this.newPrivateTelephone = newPrivateTelephone;
    }

    public String getNewMobileNumber() {
        return newMobileNumber;
    }

    public void setNewMobileNumber(String newMobileNumber) {
        this.newMobileNumber = newMobileNumber;
    }

    public String getNewEmailAddress() {
        return newEmailAddress;
    }

    public void setNewEmailAddress(String newEmailAddress) {
        this.newEmailAddress = newEmailAddress;
    }

    public String getNewDesignationId() {
        return newDesignationId;
    }

    public void setNewDesignationId(String newDesignationId) {
        this.newDesignationId = newDesignationId;
    }

    public String getNewNatureOfOccupation() {
        return newNatureOfOccupation;
    }

    public void setNewNatureOfOccupation(String newNatureOfOccupation) {
        this.newNatureOfOccupation = newNatureOfOccupation;
    }
}