package com.memberconnect.backend.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "grade5_exam_master")
public class Grade5ExamMaster {

    @Id
    @Column(name = "year")
    private Integer year;

    @Column(name = "exam_date")
    private LocalDate examDate;

    public Grade5ExamMaster() {}

    public Grade5ExamMaster(Integer year, LocalDate examDate) {
        this.year = year;
        this.examDate = examDate;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }
}