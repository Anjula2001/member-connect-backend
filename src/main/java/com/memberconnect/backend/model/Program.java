package com.memberconnect.backend.model;

import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.*;
@Entity
@Table(name = "program")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UniversityProgram> universityPrograms = new ArrayList<>();

    public Program() {}

    public Program(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<UniversityProgram> getUniversityPrograms() {
        return universityPrograms;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUniversityPrograms(List<UniversityProgram> universityPrograms) {
        this.universityPrograms = universityPrograms;
    }
}
