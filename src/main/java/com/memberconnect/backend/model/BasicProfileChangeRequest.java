package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class BasicProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String memberId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    private LocalDate newBirthDate;
    private String newNIC;
    private String newGender;
    private String newPreferredLanguage;
    private String newPermanentPrivateAddress;
    private String newMobileNumber;
    private String newEmailAddress;
    private String newDesignation;
    private String newNatureOfOccupation;
    private LocalDateTime createdDate;

    // --- Supporting Document ---
    private String documentType;        // e.g. "NIC_COPY"
    private String documentFileName;    // original file name
    private String documentFileType;    // MIME type, e.g. "application/pdf"
    private String documentStoragePath; // URL/path where the file was uploaded
    private Long   documentFileSize;    // size in bytes

    // --- Constructors ---

    public BasicProfileChangeRequest() {
    }

    public BasicProfileChangeRequest(Integer id, String memberId, ApplicationStatus newStatus,
                                     LocalDate newBirthDate, String newNIC, String newGender,
                                     String newPreferredLanguage, String newPermanentPrivateAddress,
                                     String newMobileNumber, String newEmailAddress,
                                     String newDesignation, String newNatureOfOccupation,
                                     LocalDateTime createdDate,
                                     String documentType, String documentFileName,
                                     String documentFileType, String documentStoragePath,
                                     Long documentFileSize) {
        this.id = id;
        this.memberId = memberId;
        this.newStatus = newStatus;
        this.newBirthDate = newBirthDate;
        this.newNIC = newNIC;
        this.newGender = newGender;
        this.newPreferredLanguage = newPreferredLanguage;
        this.newPermanentPrivateAddress = newPermanentPrivateAddress;
        this.newMobileNumber = newMobileNumber;
        this.newEmailAddress = newEmailAddress;
        this.newDesignation = newDesignation;
        this.newNatureOfOccupation = newNatureOfOccupation;
        this.createdDate = createdDate;
        this.documentType = documentType;
        this.documentFileName = documentFileName;
        this.documentFileType = documentFileType;
        this.documentStoragePath = documentStoragePath;
        this.documentFileSize = documentFileSize;
    }

    // --- Getters and Setters ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(ApplicationStatus newStatus) {
        this.newStatus = newStatus;
    }

    public LocalDate getNewBirthDate() {
        return newBirthDate;
    }

    public void setNewBirthDate(LocalDate newBirthDate) {
        this.newBirthDate = newBirthDate;
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

    public String getNewDesignation() {
        return newDesignation;
    }

    public void setNewDesignation(String newDesignation) {
        this.newDesignation = newDesignation;
    }

    public String getNewNatureOfOccupation() {
        return newNatureOfOccupation;
    }

    public void setNewNatureOfOccupation(String newNatureOfOccupation) {
        this.newNatureOfOccupation = newNatureOfOccupation;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    // --- Document Getters and Setters ---

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentFileName() {
        return documentFileName;
    }

    public void setDocumentFileName(String documentFileName) {
        this.documentFileName = documentFileName;
    }

    public String getDocumentFileType() {
        return documentFileType;
    }

    public void setDocumentFileType(String documentFileType) {
        this.documentFileType = documentFileType;
    }

    public String getDocumentStoragePath() {
        return documentStoragePath;
    }

    public void setDocumentStoragePath(String documentStoragePath) {
        this.documentStoragePath = documentStoragePath;
    }

    public Long getDocumentFileSize() {
        return documentFileSize;
    }

    public void setDocumentFileSize(Long documentFileSize) {
        this.documentFileSize = documentFileSize;
    }
}