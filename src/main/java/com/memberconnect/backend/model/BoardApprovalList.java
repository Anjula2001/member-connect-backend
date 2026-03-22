package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "ProcessedAt")
    private LocalDateTime processedAt;

    @Column(name = "ProcessedBy")
    private String processedBy;
}