package com.memberconnect.backend.model;

import jakarta.persistence.*;

/**
 * A Name Change Request (Requirement 02, MMC05-MMC13).
 *
 * Status, member linkage, request number, requested date and reject reason live on
 * {@link ProfileChangeRequest}. The status column here was already named "status",
 * so no @AttributeOverride is needed and existing rows keep their values.
 *
 * The "Name with initials" column was renamed to name_with_initials: a quoted
 * identifier containing spaces has to be escaped everywhere it is referenced and is
 * a standing hazard in hand-written SQL and migrations.
 */
@Table(name = "NameChangeRequestsTable")
@Entity
public class NameChangeRequest extends ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NameChangeRequestID")
    private Integer nameChangeRequestID;

    @Column(name = "title")
    private String newTitle;

    @Column(name = "fullname")
    private String newFullName;

    @Column(name = "nameAszPayroll")
    private String newNameAsInPayroll;

    @Column(name = "name_with_initials")
    private String newNameWithInitials;

    public NameChangeRequest() {
    }

    public Integer getNameChangeRequestID() {
        return nameChangeRequestID;
    }

    public void setNameChangeRequestID(Integer nameChangeRequestID) {
        this.nameChangeRequestID = nameChangeRequestID;
    }

    public String getNewTitle() {
        return newTitle;
    }

    public void setNewTitle(String newTitle) {
        this.newTitle = newTitle;
    }

    public String getNewFullName() {
        return newFullName;
    }

    public void setNewFullName(String newFullName) {
        this.newFullName = newFullName;
    }

    public String getNewNameAsInPayroll() {
        return newNameAsInPayroll;
    }

    public void setNewNameAsInPayroll(String newNameAsInPayroll) {
        this.newNameAsInPayroll = newNameAsInPayroll;
    }

    public String getNewNameWithInitials() {
        return newNameWithInitials;
    }

    public void setNewNameWithInitials(String newNameWithInitials) {
        this.newNameWithInitials = newNameWithInitials;
    }
}
