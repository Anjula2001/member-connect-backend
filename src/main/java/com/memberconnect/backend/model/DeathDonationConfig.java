package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Key/value master for the Death Donation entitlement rules (SRS 4.2.3).
 *
 * Mirrors the shape of {@link ScholarshipConfig}, but with a BigDecimal value
 * because every figure here is money or a monetary multiplier. Seeded by
 * DeathDonationConfigSeeder; see that class for the recognised keys.
 */
@Getter
@Setter
@Entity
@Table(name = "death_donation_config")
public class DeathDonationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", unique = true, nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", precision = 15, scale = 2)
    private BigDecimal configValue;

    @Column(name = "description", length = 300)
    private String description;
}
