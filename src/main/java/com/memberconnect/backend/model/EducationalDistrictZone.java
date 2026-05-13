package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "educational_district_zone",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"district", "zone"})
        }
)
public class EducationalDistrictZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String zone;

    public EducationalDistrictZone() {
    }

    public EducationalDistrictZone(String district, String zone) {
        this.district = district;
        this.zone = zone;
    }

    public Long getId() {
        return id;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }
}
