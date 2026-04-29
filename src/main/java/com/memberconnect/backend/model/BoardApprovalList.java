package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

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

    @Column(name = "ApplicationIds", columnDefinition = "TEXT")
    private String applicationIdsCsv;

    @Column(name = "Status")
    private String status;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "ProcessedAt")
    private LocalDateTime processedAt;

    @Column(name = "ProcessedBy")
    private String processedBy;
}