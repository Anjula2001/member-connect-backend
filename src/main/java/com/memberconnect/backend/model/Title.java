package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * "Title Master" — the source for the Title field on a Name Change Request
 * (Requirement 02, MMC05: Tittle, retrieved from "Title Master").
 *
 * Member.title and Member_Application.title stay free-text Strings; this master
 * governs what a user may pick in the Name Change entry, and the approved value is
 * copied across as text. That keeps the master authoritative for new input without
 * a migration of every historic member row.
 */
@Getter
@Setter
@Entity
@Table(name = "title")
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
