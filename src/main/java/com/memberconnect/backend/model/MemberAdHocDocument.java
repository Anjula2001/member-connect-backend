package com.memberconnect.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A document attached to a Member rather than to any request (Requirement 05, MMD09).
 *
 * Every other document in the system hangs off a process: UploadedDocument is keyed by
 * requestId/requestNo, and each file fills a slot the Supporting Documents for
 * Applications Master says that request needs. Ad-hoc documents are what a District
 * Office receives with no process to hang it on - a court order, a clarification letter,
 * correspondence - so they are keyed by memberId and belong to the member directly.
 *
 * Deliberately NOT stored against the member's Member_Application. Reusing that table
 * would have needed no new plumbing, but it would file member-level papers under a
 * registration they are not part of, and would break for members whose application
 * record is missing.
 *
 * There is one document type by design - MMD09 offers no type picker - so no
 * requiredDocumentId is carried. Records are immutable once written: the screen deletes
 * only files staged in the current session, which never reach this table.
 */
@Entity
@Table(name = "member_adhoc_document")
public class MemberAdHocDocument {

    /** The document type these are filed under, and the label the profile shows. */
    public static final String DOCUMENT_TYPE = "AD_HOC_DOCUMENTS";
    public static final String DISPLAY_NAME = "Ad-hoc Documents";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Member.memberId, not the numeric primary key: the business identifier is what the
     * screens carry around and what the other document tables key on.
     */
    @Column(name = "member_id", nullable = false)
    private String memberId;

    /**
     * The member's district when the document was filed, copied so access checks do not
     * have to load the Member. It is a snapshot: a later Member Transfer does not move
     * documents already on file.
     */
    @Column(name = "submission_location")
    private String submissionLocation;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    /** The storage key returned by S3Service; passed back verbatim on download. */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getSubmissionLocation() {
        return submissionLocation;
    }

    public void setSubmissionLocation(String submissionLocation) {
        this.submissionLocation = submissionLocation;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
