package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.RemittanceAccountCode;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A member's CURRENT monthly contribution for one remittance account —
 * the "Current Remittance Details" section of the Remittance & Savings tab.
 *
 * This is our data, not Finance's: it is seeded from the approved application and
 * subsequently altered by the change-remittance request flow. One row per member
 * per account code.
 */
@Getter
@Setter
@Entity
@Table(
        name = "member_remittance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "account_code"})
)
public class MemberRemittance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_code", nullable = false)
    private RemittanceAccountCode accountCode;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
