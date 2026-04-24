package com.memberconnect.backend.dto;

public class DistrictCutoffDTO {

    private Long id;
    private String district;
    private int examYear;
    private int cutoffMarks;

    // Default no-args constructor
    public DistrictCutoffDTO() {}

    // All-args constructor
    public DistrictCutoffDTO(Long id, String district, int examYear, int cutoffMarks) {
        this.id = id;
        this.district = district;
        this.examYear = examYear;
        this.cutoffMarks = cutoffMarks;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public int getExamYear() { return examYear; }
    public void setExamYear(int examYear) { this.examYear = examYear; }

    public int getCutoffMarks() { return cutoffMarks; }
    public void setCutoffMarks(int cutoffMarks) { this.cutoffMarks = cutoffMarks; }
}
