package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Board_Approval_List")
public class BoardApprovalList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String listId;

    @Column(name = "BoardMeetingId")
    private Long boardMeetingId;

    @Column(name = "BoardMeetingDate")
    private LocalDate boardMeetingDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "Board_Approval_List_Applications",
        joinColumns = @JoinColumn(name = "board_approval_list_id"),
        inverseJoinColumns = @JoinColumn(name = "member_application_id")
    )
    private java.util.List<Member_Application> applications = new java.util.ArrayList<>();

    @Column(name = "NameChangeRequestIds", columnDefinition = "TEXT")
    private String nameChangeRequestIdsCsv;

    @Column(name = "NomineeChangeRequestIds", columnDefinition = "TEXT")
    private String nomineeChangeRequestIdsCsv;

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