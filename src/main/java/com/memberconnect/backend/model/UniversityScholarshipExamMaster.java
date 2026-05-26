package com.memberconnect.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "university_scholarship_exam_master")
public class UniversityScholarshipExamMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String examYear;

    private LocalDate examLastDate;

    public Long getId() {
        return id;
    }

    public String getExamYear() {
        return examYear;
    }

    public void setExamYear(String examYear) {
        this.examYear = examYear;
    }

    public LocalDate getExamLastDate() {
        return examLastDate;
    }

    public void setExamLastDate(LocalDate examLastDate) {
        this.examLastDate = examLastDate;
    }
}