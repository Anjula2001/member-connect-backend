package com.memberconnect.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint; 

@Entity
@Table(
    name = "district_cutoff",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"district", "examYear"})
    }
)
public class DistrictCutoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String district;

    private int examYear;

    private int cutoffMarks;

    // Constructors
    public DistrictCutoff() {}

    public DistrictCutoff(String district, int examYear, int cutoffMarks) {
        this.district = district;
        this.examYear = examYear;
        this.cutoffMarks = cutoffMarks;
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public int getExamYear() {
        return examYear;
    }

    public void setExamYear(int examYear) {
        this.examYear = examYear;
    }

    public int getCutoffMarks() {
        return cutoffMarks;
    }

    public void setCutoffMarks(int cutoffMarks) {
        this.cutoffMarks = cutoffMarks;
    }
}