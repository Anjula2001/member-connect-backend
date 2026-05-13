package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents an uploaded document attached to a Death Donation Request.
 * Examples: "Death Certificate", "NIC Copy", "Other Documents"
 */
@Getter
@Setter
@Entity
@Table(name = "DeathDonationDocument")
public class DeathDonationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The parent Death Donation Request */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "death_donation_request_id", nullable = false)
    private DeathDonationRequest deathDonationRequest;

    /** Category / type label shown in the UI (e.g. "Death Certificate") */
    @Column(nullable = false, length = 100)
    private String documentType;

    /** Original file name (e.g. "death_cert.pdf") */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** MIME type (e.g. "application/pdf", "image/jpeg") */
    @Column(length = 100)
    private String mimeType;

    /** Whether this document type is mandatory before submission */
    @Column(nullable = false)
    private Boolean mandatory = false;

    /** When the file was uploaded */
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
