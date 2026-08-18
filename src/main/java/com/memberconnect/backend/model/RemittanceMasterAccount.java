package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.RemittanceAccountCode;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * "Remittance Master" — one row per remittance account collected on a New Member
 * Registration. Owned by the Accounts function (see RemittanceMasterController).
 *
 * Amount rules, per the Member Registration spec:
 *  - fixedAmount set    -> the application field is auto-filled and read-only.
 *  - minimumAmount set  -> the entered value must not be less than this.
 *  - both null          -> free entry.
 */
@Getter
@Setter
@Entity
@Table(name = "remittance_master")
public class RemittanceMasterAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_code", nullable = false, unique = true)
    private RemittanceAccountCode accountCode;

    // Label shown on the registration form for this account.
    @Column(name = "account_name", nullable = false)
    private String accountName;

    // When set, the amount is not entered by the user — it is auto-filled and locked.
    @Column(name = "fixed_amount", precision = 12, scale = 2)
    private BigDecimal fixedAmount;

    // When set, a user-entered amount must be >= this value.
    @Column(name = "minimum_amount", precision = 12, scale = 2)
    private BigDecimal minimumAmount;

    // Whether an amount must be supplied before the application can be submitted.
    @Column(name = "mandatory", nullable = false)
    private Boolean mandatory = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
