package com.memberconnect.backend.dto;

import java.time.LocalDate;
import java.util.List;

public class Grade5ExamManagementDTO {
    private Integer examYear;
    private LocalDate examDate;
    private List<DistrictCutoffDetailDTO> cutoffs;

    public Grade5ExamManagementDTO() {}

    public Grade5ExamManagementDTO(Integer examYear, LocalDate examDate, List<DistrictCutoffDetailDTO> cutoffs) {
        this.examYear = examYear;
        this.examDate = examDate;
        this.cutoffs = cutoffs;
    }

    public Integer getExamYear() {
        return examYear;
    }

    public void setExamYear(Integer examYear) {
        this.examYear = examYear;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public List<DistrictCutoffDetailDTO> getCutoffs() {
        return cutoffs;
    }

    public void setCutoffs(List<DistrictCutoffDetailDTO> cutoffs) {
        this.cutoffs = cutoffs;
    }
}
