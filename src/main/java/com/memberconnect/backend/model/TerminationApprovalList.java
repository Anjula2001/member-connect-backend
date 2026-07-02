package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "termination_approval_list_requests",
        joinColumns = @JoinColumn(name = "termination_approval_list_id"),
        inverseJoinColumns = @JoinColumn(name = "termination_request_id")
    )
    private List<TerminationRequest> requests = new ArrayList<>();

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
}
