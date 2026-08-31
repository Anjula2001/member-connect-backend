package com.memberconnect.backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * A Basic Profile Information Change Request (Requirement 02, MMC01-MMC04).
 *
 * Status, member linkage, request number, requested date and reject reason live on
 * {@link ProfileChangeRequest}. The status column keeps its original new_status name
 * via @AttributeOverride so existing rows are not orphaned by the rename.
 *
 * The old createdDate column is gone: nothing ever populated it — neither the create
 * path nor the frontend payload set it — so there was no data to preserve, and the SRS
 * asks for a "Requested Date" filled on submit, which is what requestedDate now is.
 *
 * The document* columns below are legacy. Supporting documents for all four request
 * types now go through the shared Supporting Documents master (RequiredDocument /
 * UploadedDocument) and are stored in S3, which is also what makes them readable back:
 * the old path wrote to a local uploads/ folder while the preview endpoint read from
 * S3, so an uploaded file could never be retrieved.
 */
@Entity
@AttributeOverride(name = "status", column = @Column(name = "new_status"))
public class BasicProfileChangeRequest extends ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // --- "Current Value" snapshot, taken from the Member Profile when the request is
    // --- submitted. The SRS shows this section read live from the member, but storing it
    // --- means the approver sees the values as they stood when the request was raised,
    // --- and the record explains itself later without re-deriving history.
    private LocalDate oldBirthDate;
    private String oldNIC;
    private String oldGender;
    private String oldPreferredLanguage;
    private String oldPermanentPrivateAddress;
    private String oldPrivateTelephone;
    private String oldMobileNumber;
    private String oldEmailAddress;
    private String oldDesignation;
    private String oldNatureOfOccupation;

    // --- "New Value" section ---
    private LocalDate newBirthDate;
    private String newNIC;
    private String newGender;
    private String newPreferredLanguage;
    private String newPermanentPrivateAddress;
    private String newPrivateTelephone;
    private String newMobileNumber;
    private String newEmailAddress;
    private String newDesignation;
    private String newNatureOfOccupation;

    // --- Legacy single-document columns; see the class comment. ---
    @Deprecated
    private String documentType;
    @Deprecated
    private String documentFileName;
    @Deprecated
    private String documentFileType;
    @Deprecated
    private String documentStoragePath;
    @Deprecated
    private Long documentFileSize;

    public BasicProfileChangeRequest() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getOldBirthDate() {
        return oldBirthDate;
    }

    public void setOldBirthDate(LocalDate oldBirthDate) {
        this.oldBirthDate = oldBirthDate;
    }

    public String getOldNIC() {
        return oldNIC;
    }

    public void setOldNIC(String oldNIC) {
        this.oldNIC = oldNIC;
    }

    public String getOldGender() {
        return oldGender;
    }

    public void setOldGender(String oldGender) {
        this.oldGender = oldGender;
    }

    public String getOldPreferredLanguage() {
        return oldPreferredLanguage;
    }

    public void setOldPreferredLanguage(String oldPreferredLanguage) {
        this.oldPreferredLanguage = oldPreferredLanguage;
    }

    public String getOldPermanentPrivateAddress() {
        return oldPermanentPrivateAddress;
    }

    public void setOldPermanentPrivateAddress(String oldPermanentPrivateAddress) {
        this.oldPermanentPrivateAddress = oldPermanentPrivateAddress;
    }

    public String getOldPrivateTelephone() {
        return oldPrivateTelephone;
    }

    public void setOldPrivateTelephone(String oldPrivateTelephone) {
        this.oldPrivateTelephone = oldPrivateTelephone;
    }

    public String getOldMobileNumber() {
        return oldMobileNumber;
    }

    public void setOldMobileNumber(String oldMobileNumber) {
        this.oldMobileNumber = oldMobileNumber;
    }

    public String getOldEmailAddress() {
        return oldEmailAddress;
    }

    public void setOldEmailAddress(String oldEmailAddress) {
        this.oldEmailAddress = oldEmailAddress;
    }

    public String getOldDesignation() {
        return oldDesignation;
    }

    public void setOldDesignation(String oldDesignation) {
        this.oldDesignation = oldDesignation;
    }

    public String getOldNatureOfOccupation() {
        return oldNatureOfOccupation;
    }

    public void setOldNatureOfOccupation(String oldNatureOfOccupation) {
        this.oldNatureOfOccupation = oldNatureOfOccupation;
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

    /** MMC01 lists this among the changeable fields; it was missing entirely. Optional. */
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

    // --- Legacy document accessors ---

    @Deprecated
    public String getDocumentType() {
        return documentType;
    }

    @Deprecated
    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    @Deprecated
    public String getDocumentFileName() {
        return documentFileName;
    }

    @Deprecated
    public void setDocumentFileName(String documentFileName) {
        this.documentFileName = documentFileName;
    }

    @Deprecated
    public String getDocumentFileType() {
        return documentFileType;
    }

    @Deprecated
    public void setDocumentFileType(String documentFileType) {
        this.documentFileType = documentFileType;
    }

    @Deprecated
    public String getDocumentStoragePath() {
        return documentStoragePath;
    }

    @Deprecated
    public void setDocumentStoragePath(String documentStoragePath) {
        this.documentStoragePath = documentStoragePath;
    }

    @Deprecated
    public Long getDocumentFileSize() {
        return documentFileSize;
    }

    @Deprecated
    public void setDocumentFileSize(Long documentFileSize) {
        this.documentFileSize = documentFileSize;
    }
}
