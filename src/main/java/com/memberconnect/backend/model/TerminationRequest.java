package com.memberconnect.backend.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.memberconnect.backend.enums.TerminationRequestStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "termination_request",
    indexes = {
        @Index(name = "idx_termination_request_status_date", columnList = "status, requested_date"),
        @Index(name = "idx_termination_request_member_id", columnList = "member_id"),
        @Index(name = "idx_termination_request_location", columnList = "submission_location")
    }
)
public class TerminationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", unique = true, nullable = false)
    private String requestNo;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    /**
     * The District Office branch that raised this request, copied from
     * Member.submissionLocation when the request is created (MMT02 location
     * filter).
     *
     * Copied rather than joined so the request keeps the office that actually
     * handled it even if the member is later administered elsewhere - a board
     * list must still show where each request came from.
     *
     * Nullable: rows created before this column existed have no value, and are
     * treated as visible to Head Office only until they are backfilled.
     */
    @Column(name = "submission_location")
    private String submissionLocation;

    /**
     * The status this request held immediately before it was added to a
     * Termination Approval List.
     *
     * MMT07 requires that deleting a list rolls its requests back "to the
     * Submitted for Approval status or Rejected status depend on what status it
     * was originally" - which cannot be derived after the fact, because both
     * arrive at ADDED_TO_APPROVAL_LIST. Recording it on the way in is the only
     * way to put it back correctly.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private TerminationRequestStatus previousStatus;

    // Legacy Phase 1 columns. Both are still written on every save and are the
    // only reason data requests created before the Termination Reasons Master
    // have, so neither is dropped or retyped until the backfill is agreed.
    @Column(name = "termination_reason_id", nullable = false)
    private String terminationReasonId;

    @Column(name = "termination_reason", nullable = false)
    private String terminationReason;

    // Nullable throughout Phase 1: existing rows have no master reference yet.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "termination_reason_ref_id")
    private TerminationReason terminationReasonRef;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TerminationRequestStatus status;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "incomplete_reason", columnDefinition = "TEXT")
    private String incompleteReason;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @OneToMany(mappedBy = "terminationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TerminationMinorDisbursement> minorDisbursements = new ArrayList<>();

    public Long getId() {
        return id;
    }

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

    public String getSubmissionLocation() {
        return submissionLocation;
    }

    public void setSubmissionLocation(String submissionLocation) {
        this.submissionLocation = submissionLocation;
    }

    public TerminationRequestStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(TerminationRequestStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getTerminationReasonId() {
        return terminationReasonId;
    }

    public void setTerminationReasonId(String terminationReasonId) {
        this.terminationReasonId = terminationReasonId;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public TerminationReason getTerminationReasonRef() {
        return terminationReasonRef;
    }

    public void setTerminationReasonRef(TerminationReason terminationReasonRef) {
        this.terminationReasonRef = terminationReasonRef;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public void setRequestedDate(LocalDate requestedDate) {
        this.requestedDate = requestedDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public TerminationRequestStatus getStatus() {
        return status;
    }

    public void setStatus(TerminationRequestStatus status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getIncompleteReason() {
        return incompleteReason;
    }

    public void setIncompleteReason(String incompleteReason) {
        this.incompleteReason = incompleteReason;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public List<TerminationMinorDisbursement> getMinorDisbursements() {
        return minorDisbursements;
    }

    public void setMinorDisbursements(List<TerminationMinorDisbursement> minorDisbursements) {
        this.minorDisbursements = minorDisbursements;
    }
}
