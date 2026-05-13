package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "MemberDeathDocument")
public class MemberDeathDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_death_record_id", nullable = false)
    private MemberDeathRecord memberDeathRecord;

    @Column(nullable = false, length = 100)
    private String documentType;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 100)
    private String mimeType;

    @Column(nullable = false)
    private Boolean mandatory = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
