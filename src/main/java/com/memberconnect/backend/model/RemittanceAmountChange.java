package com.memberconnect.backend.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

/**
 * A Remittance Amount Change Request (Requirement 02, MMC14-MMC17).
 *
 * Status, member linkage, request number, requested date and reject reason live on
 * {@link ProfileChangeRequest}. The status column keeps its original new_status name
 * via @AttributeOverride so existing rows are not orphaned by the rename.
 *
 * NOTE: the newRemittanceAmount / remittanceAccountType pair below cannot express what
 * MMC14 asks for — a request covering several remittance accounts, each with its own
 * amount validated against that account's configured minimum. The entry screen was
 * already collecting multiple rows and then flattening them, storing the summed total
 * as the amount and the first row's type as the account type, discarding the rest.
 * These two fields are retained only until the per-account line items land; do not
 * build anything new on them.
 */
@Entity
@Table(name = "RemittanceAmountChange")
@AttributeOverride(name = "status", column = @Column(name = "new_status"))
public class RemittanceAmountChange extends ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * One row per editable account (MMC14). Replaces the single flattened amount below,
     * which could not express "an amount per account" at all.
     */
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("accountCode ASC")
    private List<RemittanceChangeRequestLine> lines = new ArrayList<>();

    public List<RemittanceChangeRequestLine> getLines() {
        return lines;
    }

    public void setLines(List<RemittanceChangeRequestLine> lines) {
        this.lines.clear();
        if (lines != null) {
            lines.forEach(this::addLine);
        }
    }

    /** Keeps both sides of the relationship in step, which orphanRemoval relies on. */
    public void addLine(RemittanceChangeRequestLine line) {
        line.setRequest(this);
        this.lines.add(line);
    }

    /** @deprecated flattened total; replaced by per-account line items. */
    @Deprecated
    private String newRemittanceAmount;

    private String newRemittanceCurrency;

    /** @deprecated only ever held the first selected account; see the class comment. */
    @Deprecated
    private String remittanceAccountType;

    public RemittanceAmountChange() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Deprecated
    public String getNewRemittanceAmount() {
        return newRemittanceAmount;
    }

    @Deprecated
    public void setNewRemittanceAmount(String newRemittanceAmount) {
        this.newRemittanceAmount = newRemittanceAmount;
    }

    public String getNewRemittanceCurrency() {
        return newRemittanceCurrency;
    }

    public void setNewRemittanceCurrency(String newRemittanceCurrency) {
        this.newRemittanceCurrency = newRemittanceCurrency;
    }

    @Deprecated
    public String getRemittanceAccountType() {
        return remittanceAccountType;
    }

    @Deprecated
    public void setRemittanceAccountType(String remittanceAccountType) {
        this.remittanceAccountType = remittanceAccountType;
    }
}
