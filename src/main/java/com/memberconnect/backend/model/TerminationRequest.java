package com.memberconnect.backend.model;

import java.time.LocalDate;

import com.memberconnect.backend.enums.TerminationRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "termination_request")
public class TerminationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", unique = true, nullable = false)
    private String requestNo;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "termination_reason_id", nullable = false)
    private String terminationReasonId;

    @Column(name = "termination_reason", nullable = false)
    private String terminationReason;

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
}
