package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a close relative (who is also a system member) linked to a Death Donation Request.
 *
 * isAuto = true  → fetched automatically by the system (cannot be deleted by the user)
 * isAuto = false → added manually by the user
 */
@Getter
@Setter
@Entity
@Table(name = "DeathDonationRelative")
public class DeathDonationRelative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The parent Death Donation Request */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "death_donation_request_id", nullable = false)
    private DeathDonationRequest deathDonationRequest;

    /** Member ID of the relative (e.g. "MEM-12345") */
    @Column(nullable = false, length = 50)
    private String memberId;

    /** Relationship of this relative to the deceased (Brother, Sister, Spouse …) */
    @Column(nullable = false, length = 100)
    private String relationshipToDeceased;

    /**
     * true  → system-detected (read-only, shown with "Auto" label in UI)
     * false → manually added by the user (can be removed)
     */
    @Column(nullable = false)
    private Boolean isAuto = false;
}
