package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.AccountDataSource;
import com.memberconnect.backend.enums.RemittanceAccountCode;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An operative account held by a member — the "Operative Accounts" section of the
 * Remittance & Savings tab (account number and current balance).
 *
 * This data properly belongs to the Finance Module, which is outside this project.
 * Until that integration exists it is entered by hand from the admin screen; the
 * {@code source} and {@code lastSyncedAt} columns exist so a later Finance sync can
 * overwrite these rows and make it obvious which are still hand-entered.
 */
@Getter
@Setter
@Entity
@Table(
        name = "member_account",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "account_code"})
)
public class MemberAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_code", nullable = false)
    private RemittanceAccountCode accountCode;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "balance", precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AccountDataSource source = AccountDataSource.MANUAL;

    /** Set when the Finance Module last wrote this row; null while hand-entered. */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
