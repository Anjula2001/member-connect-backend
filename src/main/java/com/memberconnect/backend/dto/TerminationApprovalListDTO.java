package com.memberconnect.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TerminationApprovalListDTO {

    private Long id;
    private String listId;
    private Long boardMeetingId;
    private LocalDate boardMeetingDate;
    private List<String> requestNos = new ArrayList<>();
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private LocalDate actualMeetingDate;
    private String decision;
    private String rejectReason;
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

    public List<String> getRequestNos() {
        return requestNos;
    }

    public void setRequestNos(List<String> requestNos) {
        this.requestNos = requestNos;
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
