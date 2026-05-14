package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.ApplicationStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "NommineChangeRequests")
public class NommineChangeRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;// Changed to ApplicationStatus enum for type safety
    private String newnommineName;
    private String relationship;
    private String nic;
    private String address;

    // --- 1. No-Args Constructor (Required by JPA) ---
    public NommineChangeRequests() {
    }

    // --- 2. All-Args Constructor ---
    public NommineChangeRequests(Integer id, ApplicationStatus status, String newnommineName,
                                 String relationship, String nic, String address) {
        this.id = id;
        this.status = status;
        this.newnommineName = newnommineName;
        this.relationship = relationship;
        this.nic = nic;
        this.address = address;
    }

    // --- 3. Getters and Setters ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
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

    // --- 4. toString (Optional but very helpful for debugging) ---
}
