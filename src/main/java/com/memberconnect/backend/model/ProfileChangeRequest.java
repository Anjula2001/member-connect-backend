package com.memberconnect.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.memberconnect.backend.enums.ApplicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

/**
 * The fields every Member Profile Change Request carries, per Requirement 02.
 *
 * The four request types were each built separately and ended up agreeing on almost
 * nothing: two had a memberId and two had none at all, none stored the Request ID the
 * SRS requires on submit, only one had any kind of date and it was never populated,
 * and the reject reason the SRS asks for on every reject existed nowhere. This class
 * is that common spine, so the four cannot drift apart on it again.
 *
 * It is a @MappedSuperclass rather than an @Inheritance hierarchy on purpose: the four
 * requests share a shape, not a table, and each already has its own table with live
 * data and its own primary key column name. Subclasses use @AttributeOverride where
 * their existing column name differs from the default here, so adopting this spine
 * does not rename a column out from under existing rows.
 */
@MappedSuperclass
public abstract class ProfileChangeRequest {

    /**
     * The Request ID shown to users (PCR-2026-001). Null until submit — the SRS says
     * a new request "will display as 'NEW' since no ID is created yet". Nullable and
     * unique together are fine: Postgres allows many nulls in a unique index.
     */
    @Column(name = "request_no", unique = true)
    private String requestNo;

    /** The membership number (Member.memberId), not the Member table's primary key. */
    @Column(name = "member_id")
    private String memberId;

    /** "Auto filled with the current system date" on submit. */
    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ApplicationStatus status;

    /**
     * The status held immediately before the request was added to a board approval
     * list, so deleting that list can put it back.
     *
     * MMC10 and MMC23 both say a deleted list rolls its requests back "to the
     * 'Submitted for Approval' status or 'Rejected' status depend on what status it was
     * originally". Without somewhere to record the original, delete could only ever
     * write Submitted for Approval - which erased a prior rejection, even though
     * MMC08/MMC21 explicitly allow Rejected requests onto a new list.
     *
     * Nullable: rows listed before this column existed have nothing recorded, and fall
     * back to Submitted for Approval as before. Mirrors
     * Member_Application.statusBeforeBoardList, which solved the same problem.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_board_list")
    private ApplicationStatus statusBeforeBoardList;

    /** Required whenever the request is rejected (MMC04, MMC12, MMC17, MMC25). */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /**
     * The district office the request was raised at. Drives the Location filter on the
     * unified list, which locks district users to their own location.
     */
    @Column(name = "submission_location")
    private String submissionLocation;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public ApplicationStatus getStatusBeforeBoardList() {
        return statusBeforeBoardList;
    }

    public void setStatusBeforeBoardList(ApplicationStatus statusBeforeBoardList) {
        this.statusBeforeBoardList = statusBeforeBoardList;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getSubmissionLocation() {
        return submissionLocation;
    }

    public void setSubmissionLocation(String submissionLocation) {
        this.submissionLocation = submissionLocation;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
