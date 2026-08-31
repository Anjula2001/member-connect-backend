package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "working_location")
public class WorkingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "salary_paying_office")
    private String salaryPayingOffice;

    @ManyToOne
    @JoinColumn(name = "working_location_type_id")
    private WorkingLocationType workingLocationType;

    @ManyToOne
    @JoinColumn(name = "educational_district_id")
    private EducationalDistrict educationalDistrict;

    @ManyToOne
    @JoinColumn(name = "educational_zone_id")
    private EducationalZone educationalZone;
}