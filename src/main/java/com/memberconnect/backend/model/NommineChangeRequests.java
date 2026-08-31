package com.memberconnect.backend.model;

import jakarta.persistence.*;

/**
 * A Nominee Change Request (Requirement 02, MMC18-MMC26).
 *
 * Status, member linkage, request number, requested date and reject reason all live
 * on {@link ProfileChangeRequest}. The status column was already named "status" here,
 * so no @AttributeOverride is needed and existing rows keep their values.
 */
@Entity
@Table(name = "NommineChangeRequests")
public class NommineChangeRequests extends ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // --- "Current Value" snapshot, taken from the Member Profile when the request is
    // --- submitted. MMC18 shows this section read live from the member; storing it means
    // --- the board sees the nominee as they stood when the request was raised.
    private String oldNommineName;

    private String oldRelationship;

    private String oldNic;

    private String oldAddress;

    // --- "New Value" section ---
    private String newnommineName;

    private String relationship;

    private String nic;

    private String address;

    // --- Supporting document (MMC18's "Upload Supporting Documents" section).
    // --- Stored in S3 via S3Service, the same store every other module uses; the
    // --- column holds the object key, not the bytes.
    private String documentType;
    private String documentFileName;
    private String documentFileType;
    private String documentStoragePath;
    private Long documentFileSize;

    public NommineChangeRequests() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOldNommineName() {
        return oldNommineName;
    }

    public void setOldNommineName(String oldNommineName) {
        this.oldNommineName = oldNommineName;
    }

    public String getOldRelationship() {
        return oldRelationship;
    }

    public void setOldRelationship(String oldRelationship) {
        this.oldRelationship = oldRelationship;
    }

    public String getOldNic() {
        return oldNic;
    }

    public void setOldNic(String oldNic) {
        this.oldNic = oldNic;
    }

    public String getOldAddress() {
        return oldAddress;
    }

    public void setOldAddress(String oldAddress) {
        this.oldAddress = oldAddress;
    }

    public String getNewnommineName() {
        return newnommineName;
    }

    public void setNewnommineName(String newnommineName) {
        this.newnommineName = newnommineName;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

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

    /** The S3 object key. */
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
