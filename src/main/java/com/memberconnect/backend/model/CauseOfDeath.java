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

@Getter
@Setter
@Entity
@Table(name = "cause_of_death")
public class CauseOfDeath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Per-cause override of the general Death Donation maximum (SRS 4.2.3):
     * "if the selected 'Cause of Death' has a maximum death donation amount
     * configured in the Cause of Death Master, that value will be selected
     * ignoring the general death donation value configured in the system."
     *
     * Nullable - when null the DeathDonationConfig DEFAULT_MAX_DONATION applies.
     */
    @Column(name = "max_death_donation_amount", precision = 15, scale = 2)
    private BigDecimal maxDeathDonationAmount;
}
