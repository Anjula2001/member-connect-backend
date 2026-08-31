package com.memberconnect.backend.dto;

public class DistrictCutoffDetailDTO {
    private String district;
    private Integer cutoffMarks;

    public DistrictCutoffDetailDTO() {}

    public DistrictCutoffDetailDTO(String district, Integer cutoffMarks) {
        this.district = district;
        this.cutoffMarks = cutoffMarks;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public Integer getCutoffMarks() {
        return cutoffMarks;
    }

    public void setCutoffMarks(Integer cutoffMarks) {
        this.cutoffMarks = cutoffMarks;
    }
}
