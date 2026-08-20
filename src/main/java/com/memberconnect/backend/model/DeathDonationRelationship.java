package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The Death Donation Relationship master (SRS MMD01): "The relationship of the
 * decreased to the Member is selected from the dropdown", retrieved from the
 * "Death Donation Relationship master".
 *
 * Before this existed the relationship was free text on the request and a
 * hardcoded list of ten options in the browser, so the two could drift and
 * nothing stopped an arbitrary string being persisted.
 *
 * {@code displayOrder} exists because the natural reading order of a family
 * relationship list (Father, Mother, Spouse, ...) is neither alphabetical nor
 * insertion order.
 */
@Getter
@Setter
@Entity
@Table(name = "death_donation_relationship")
public class DeathDonationRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
