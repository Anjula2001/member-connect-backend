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
 * Single-row configuration for who is eligible to become a Member.
 *
 * The registration spec requires the applicant's age (derived from Date of Birth
 * against the current system date) to fall inside a configurable limit, checked on
 * both Save and Submit. Same single-row pattern as DormantConfig/ScholarshipConfig.
 */
@Getter
@Setter
@Entity
@Table(name = "membership_eligibility_config")
public class MembershipEligibilityConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "minimum_age", nullable = false)
    private Integer minimumAge = 18;

    @Column(name = "maximum_age", nullable = false)
    private Integer maximumAge = 60;
}
