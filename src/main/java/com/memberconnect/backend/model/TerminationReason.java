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
 * Termination Reasons Master (SRS Section 2 - Member Terminations, MMT01).
 *
 * Mirrors the CauseOfDeath master: rows are retired by clearing "active"
 * rather than deleted, so a termination request approved years ago keeps
 * pointing at a reason that still exists.
 */
@Getter
@Setter
@Entity
@Table(name = "termination_reason")
public class TerminationReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order")
    private Integer displayOrder;
}
