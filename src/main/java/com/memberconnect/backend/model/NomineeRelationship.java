package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * "Nominee Relationship Master" — the source for the Relationship field on a
 * Nominee Change Request (Requirement 02, MMC18: Relationship, retrieved from
 * "Nominee Relationship Master").
 *
 * Replaces the four values that were hardcoded in the frontend component
 * (spouse / child / parent / sibling), which could not be extended without a
 * code change and did not match Member.nomineeRelationship's casing.
 */
@Getter
@Setter
@Entity
@Table(name = "nominee_relationship")
public class NomineeRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
