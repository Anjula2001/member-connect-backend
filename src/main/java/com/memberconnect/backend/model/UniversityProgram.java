package com.memberconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "university_programs")
public class UniversityProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "scholarship_amount")
    private Double scholarshipAmount;

    public UniversityProgram() {}

    public UniversityProgram(University university, Program program, Integer duration) {
        this.university = university;
        this.program = program;
        this.duration = duration;
    }

    public Long getId() {
        return id;
    }

    public University getUniversity() {
        return university;
    }

    public Program getProgram() {
        return program;
    }

    public Integer getDuration() {
        return duration;
    }

    public Double getScholarshipAmount() {
        return scholarshipAmount;
    }

    public void setUniversity(University university) {
        this.university = university;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public void setScholarshipAmount(Double scholarshipAmount) {
        this.scholarshipAmount = scholarshipAmount;
    }
}
