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
 * One band of the "pre-defined criteria in the system based on the number of
 * months the remittance has been deducted" that the SRS (4.2.3) uses to derive
 * the Eligible Death Donation Amount from the maximum.
 *
 * The applicable tier is the one with the highest {@code minMonths} that does
 * not exceed the member's months remitted.
 */
@Getter
@Setter
@Entity
@Table(name = "death_donation_eligibility_tier")
public class DeathDonationEligibilityTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Inclusive lower bound, in months of remittance deducted. */
    @Column(name = "min_months", unique = true, nullable = false)
    private Integer minMonths;

    /** Percentage of the maximum donation payable at this tier, 0-100. */
    @Column(name = "percentage", precision = 6, scale = 2, nullable = false)
    private BigDecimal percentage;
}
