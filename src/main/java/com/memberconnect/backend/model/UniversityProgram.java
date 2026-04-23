package com.memberconnect.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "university_programs")
public class UniversityProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false)
    private Integer duration;

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

    public void setUniversity(University university) {
        this.university = university;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}