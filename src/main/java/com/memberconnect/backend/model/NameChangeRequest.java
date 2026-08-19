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

    // --- "Current Value" snapshot, taken from the Member Profile when the request is
    // --- submitted. MMC05 shows this section read live from the member; storing it means
    // --- the board sees the names as they stood when the request was raised.
    @Column(name = "old_title")
    private String oldTitle;

    @Column(name = "old_fullname")
    private String oldFullName;

    @Column(name = "old_name_as_payroll")
    private String oldNameAsInPayroll;

    @Column(name = "old_name_with_initials")
    private String oldNameWithInitials;

    // --- "New Value" section ---
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

    public String getOldTitle() {
        return oldTitle;
    }

    public void setOldTitle(String oldTitle) {
        this.oldTitle = oldTitle;
    }

    public String getOldFullName() {
        return oldFullName;
    }

    public void setOldFullName(String oldFullName) {
        this.oldFullName = oldFullName;
    }

    public String getOldNameAsInPayroll() {
        return oldNameAsInPayroll;
    }

    public void setOldNameAsInPayroll(String oldNameAsInPayroll) {
        this.oldNameAsInPayroll = oldNameAsInPayroll;
    }

    public String getOldNameWithInitials() {
        return oldNameWithInitials;
    }

    public void setOldNameWithInitials(String oldNameWithInitials) {
        this.oldNameWithInitials = oldNameWithInitials;
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
