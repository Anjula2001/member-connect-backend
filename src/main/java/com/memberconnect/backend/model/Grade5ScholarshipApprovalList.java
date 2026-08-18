package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Grade5_Scholarship_Approval_List")
public class Grade5ScholarshipApprovalList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, name = "ListId")
    private String listId;

    @Column(name = "BoardMeetingId")
    private Long boardMeetingId;

    @Column(name = "BoardMeetingDate")
    private LocalDate boardMeetingDate;

    @Column(name = "ActualMeetingDate")
    private LocalDate actualMeetingDate;

    @Column(name = "Status")
    private String status; // CREATED, PROCESSED

    @Column(name = "Type")
    private String type; // NORMAL, DEVIATION

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "ProcessedAt")
    private LocalDateTime processedAt;

    @Column(name = "ProcessedBy")
    private String processedBy;

    @Column(name = "ScannedReportPath")
    private String scannedReportPath;

    @Column(name = "Decision")
    private String decision;

    @Column(name = "BoardRemarks", length = 2000)
    private String boardRemarks;
}
