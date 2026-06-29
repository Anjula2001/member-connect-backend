package com.memberconnect.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "termination_approval_list")
public class TerminationApprovalList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String listId;

    @Column(name = "board_meeting_id")
    private Long boardMeetingId;

    @Column(name = "board_meeting_date")
    private LocalDate boardMeetingDate;

    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TerminationApprovalListItem> items = new ArrayList<>();

    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "actual_meeting_date")
    private LocalDate actualMeetingDate;

    @Column(name = "decision")
    private String decision;

    @Column(name = "reject_reason", length = 2000)
    private String rejectReason;

    @Column(name = "board_remarks", length = 2000)
    private String boardRemarks;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getListId() {
        return listId;
    }

    public void setListId(String listId) {
        this.listId = listId;
    }

    public Long getBoardMeetingId() {
        return boardMeetingId;
    }

    public void setBoardMeetingId(Long boardMeetingId) {
        this.boardMeetingId = boardMeetingId;
    }

    public LocalDate getBoardMeetingDate() {
        return boardMeetingDate;
    }

    public void setBoardMeetingDate(LocalDate boardMeetingDate) {
        this.boardMeetingDate = boardMeetingDate;
    }

    public List<TerminationApprovalListItem> getItems() {
        return items;
    }

    public void setItems(List<TerminationApprovalListItem> items) {
        this.items = items;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDate getActualMeetingDate() {
        return actualMeetingDate;
    }

    public void setActualMeetingDate(LocalDate actualMeetingDate) {
        this.actualMeetingDate = actualMeetingDate;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getBoardRemarks() {
        return boardRemarks;
    }

    public void setBoardRemarks(String boardRemarks) {
        this.boardRemarks = boardRemarks;
    }
}
