package com.memberconnect.backend.model;

import jakarta.persistence.*;

/**
 * A Nominee Change Request (Requirement 02, MMC18-MMC26).
 *
 * Status, member linkage, request number, requested date and reject reason all live
 * on {@link ProfileChangeRequest}. The status column was already named "status" here,
 * so no @AttributeOverride is needed and existing rows keep their values.
 */
@Entity
@Table(name = "NommineChangeRequests")
public class NommineChangeRequests extends ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String newnommineName;

    private String relationship;

    private String nic;

    private String address;

    public NommineChangeRequests() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNewnommineName() {
        return newnommineName;
    }

    public void setNewnommineName(String newnommineName) {
        this.newnommineName = newnommineName;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
