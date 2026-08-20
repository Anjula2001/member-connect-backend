package com.memberconnect.backend.model;

import java.math.BigDecimal;

import com.memberconnect.backend.enums.RemittanceAccountCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One account's before/after amount within a Remittance Amount Change Request
 * (Requirement 02, MMC14).
 *
 * MMC14 asks for an amount per editable account, each validated against that account's
 * configured minimum. The request previously held a single newRemittanceAmount - a
 * String - plus one remittanceAccountType, so the screen collected several rows and
 * then flattened them: the amount became the total and the type became whichever
 * account happened to be first. Per-account detail was discarded on save, which also
 * meant an approval had nothing specific to write back.
 *
 * oldAmount is the member's amount for this account at the moment the request was
 * submitted, so the approver compares against what stood then rather than what stands
 * now - the same reasoning as the old* snapshots on the other three request types.
 */
@Getter
@Setter
@Entity
@Table(name = "remittance_change_request_line")
public class RemittanceChangeRequestLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private RemittanceAmountChange request;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_code", nullable = false)
    private RemittanceAccountCode accountCode;

    /** The member's amount when the request was raised. Null if they had no such account. */
    @Column(name = "old_amount", precision = 12, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal newAmount = BigDecimal.ZERO;
}
