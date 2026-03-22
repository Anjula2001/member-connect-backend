package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Audit")
public class Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String auditId;

    @Column(name = "EntityName")
    private String entityName;

    @Column(name = "EntityId")
    private String entityId;

    @Column(name = "Action")
    private String action;

    @Column(name = "ChangedBy")
    private String changedBy;

    @Column(name = "ChangedAt")
    private LocalDateTime changedAt;

    @Column(name = "Details", length = 1000)
    private String details;
}