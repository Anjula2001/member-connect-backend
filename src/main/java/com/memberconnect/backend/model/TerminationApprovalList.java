package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Termination_Approval_List")
public class TerminationApprovalList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String listId;

    @Column(name = "BoardMeetingId")
    private Long boardMeetingId;

    @Column(name = "BoardMeetingDate")
    private LocalDate boardMeetingDate;

    // Comma-separated termination IDs (String IDs like "TERM-123456")
    @Column(name = "TerminationIds", columnDefinition = "TEXT")
    private String terminationIdsCsv;

    @Column(name = "Status")
    private String status;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "ProcessedAt")
    private LocalDateTime processedAt;

    @Column(name = "ProcessedBy")
    private String processedBy;

    @Column(name = "ActualMeetingDate")
    private LocalDate actualMeetingDate;

    @Column(name = "Decision")
    private String decision;

    @Column(name = "RejectReason", length = 2000)
    private String rejectReason;

    @Column(name = "BoardRemarks", length = 2000)
    private String boardRemarks;
}
