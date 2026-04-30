package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.Designation;
import com.memberconnect.backend.model.EducationalDistrict;
import com.memberconnect.backend.model.EducationalZone;
import com.memberconnect.backend.model.NatureOfOccupation;
import com.memberconnect.backend.model.WorkingLocation;
import com.memberconnect.backend.model.WorkingLocationType;
import com.memberconnect.backend.repository.DesignationRepository;
import com.memberconnect.backend.repository.EducationalDistrictRepository;
import com.memberconnect.backend.repository.EducationalZoneRepository;
import com.memberconnect.backend.repository.NatureOfOccupationRepository;
import com.memberconnect.backend.repository.WorkingLocationRepository;
import com.memberconnect.backend.repository.WorkingLocationTypeRepository;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masters")
@CrossOrigin(origins = "http://localhost:3000")
public class MastersController {

    private final WorkingLocationTypeRepository workingLocationTypeRepository;
    private final EducationalDistrictRepository educationalDistrictRepository;
    private final EducationalZoneRepository educationalZoneRepository;
    private final WorkingLocationRepository workingLocationRepository;
    private final DesignationRepository designationRepository;
    private final NatureOfOccupationRepository natureOfOccupationRepository;

    public MastersController(
            WorkingLocationTypeRepository workingLocationTypeRepository,
            EducationalDistrictRepository educationalDistrictRepository,
            EducationalZoneRepository educationalZoneRepository,
            WorkingLocationRepository workingLocationRepository,
            DesignationRepository designationRepository,
            NatureOfOccupationRepository natureOfOccupationRepository
    ) {
        this.workingLocationTypeRepository = workingLocationTypeRepository;
        this.educationalDistrictRepository = educationalDistrictRepository;
        this.educationalZoneRepository = educationalZoneRepository;
        this.workingLocationRepository = workingLocationRepository;
        this.designationRepository = designationRepository;
        this.natureOfOccupationRepository = natureOfOccupationRepository;
    }

    @GetMapping("/working-location-types")
    public List<WorkingLocationType> getWorkingLocationTypes() {
        return workingLocationTypeRepository.findAll();
    }

    @GetMapping("/districts")
    public List<EducationalDistrict> getDistricts() {
        return educationalDistrictRepository.findAll();
    }

    @GetMapping("/zones")
    public List<EducationalZone> getZones() {
        return educationalZoneRepository.findAll();
    }

    @GetMapping("/working-locations")
    public List<WorkingLocation> getWorkingLocations() {
        return workingLocationRepository.findAll();
    }

    @GetMapping("/designations")
    public List<Designation> getDesignations() {
        return designationRepository.findAll();
    }

    @GetMapping("/nature-of-occupations")
    public List<NatureOfOccupation> getNatureOfOccupations() {
        return natureOfOccupationRepository.findAll();
    }
}