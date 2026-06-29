package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.EducationalDistrict;
import com.memberconnect.backend.model.EducationalZone;
import com.memberconnect.backend.model.Designation;
import com.memberconnect.backend.model.NatureOfOccupation;
import com.memberconnect.backend.model.WorkingLocation;
import com.memberconnect.backend.model.WorkingLocationType;
import com.memberconnect.backend.repository.EducationalDistrictRepository;
import com.memberconnect.backend.repository.EducationalZoneRepository;
import com.memberconnect.backend.repository.DesignationRepository;
import com.memberconnect.backend.repository.NatureOfOccupationRepository;
import com.memberconnect.backend.repository.WorkingLocationRepository;
import com.memberconnect.backend.repository.WorkingLocationTypeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/masters")
@CrossOrigin(origins = "http://localhost:3000")
public class MastersController {

    private final WorkingLocationTypeRepository workingLocationTypeRepository;
    private final EducationalDistrictRepository educationalDistrictRepository;
    private final EducationalZoneRepository educationalZoneRepository;
    private final DesignationRepository designationRepository;
    private final NatureOfOccupationRepository natureOfOccupationRepository;
    private final WorkingLocationRepository workingLocationRepository;

    public MastersController(
            WorkingLocationTypeRepository workingLocationTypeRepository,
            EducationalDistrictRepository educationalDistrictRepository,
            EducationalZoneRepository educationalZoneRepository,
            DesignationRepository designationRepository,
            NatureOfOccupationRepository natureOfOccupationRepository,
            WorkingLocationRepository workingLocationRepository
    ) {
        this.workingLocationTypeRepository = workingLocationTypeRepository;
        this.educationalDistrictRepository = educationalDistrictRepository;
        this.educationalZoneRepository = educationalZoneRepository;
        this.designationRepository = designationRepository;
        this.natureOfOccupationRepository = natureOfOccupationRepository;
        this.workingLocationRepository = workingLocationRepository;
    }

    // Endpoint to get all working location types
    @GetMapping("/working-location-types")
    public List<WorkingLocationType> getWorkingLocationTypes() {
        return workingLocationTypeRepository.findAll();
    }

    // Endpoint to get all educational districts
    @GetMapping("/districts")
    public List<EducationalDistrict> getDistricts() {
        return educationalDistrictRepository.findAll();
    }
 
    // Endpoint to get all designations
    @GetMapping("/designations")
    public List<Designation> getDesignations() {
        return designationRepository.findAll();
    }

    // Endpoint to get all nature of occupations
    @GetMapping("/nature-of-occupations")
    public List<NatureOfOccupation> getNatureOfOccupations() {
        return natureOfOccupationRepository.findAll();
    }

    // Endpoint to get termination reasons
    @GetMapping("/termination-reasons")
    public List<Map<String, String>> getTerminationReasons() {
        return List.of(
                Map.of("id", "1", "name", "Resignation from Post"),
                Map.of("id", "2", "name", "Disciplinary Action"),
                Map.of("id", "3", "name", "Transfer to Another Organization"),
                Map.of("id", "4", "name", "Other")
        );
    }

    // Endpoint to get educational zones by district ID or name
    @GetMapping("/educational-zones")
    public List<EducationalZone> getEducationalZonesByDistrict(
            @RequestParam String district
    ) {
        Long districtId = parseLongOrNull(district);

        if (districtId != null) {
            return educationalZoneRepository.findByEducationalDistrictId(districtId);
        }

        return educationalZoneRepository.findByEducationalDistrictNameIgnoreCase(district);
    }

    // Endpoint to get working locations by type, district, and optionally zone
    @GetMapping("/working-locations")
    public List<WorkingLocation> getWorkingLocations(
            @RequestParam String type,
            @RequestParam String district,
            @RequestParam(required = false) String zone
    ) {
        WorkingLocationType locationType = findWorkingLocationType(type);

        boolean usesZone = Boolean.TRUE.equals(locationType.getUsesZone());

        Long typeId = locationType.getId();
        Long districtId = parseLongOrNull(district);
        Long zoneId = parseLongOrNull(zone);

        if (districtId != null) {
            if (usesZone && zoneId != null) {
                return workingLocationRepository
                        .findByWorkingLocationTypeIdAndEducationalDistrictIdAndEducationalZoneId(
                                typeId,
                                districtId,
                                zoneId
                        );
            }

            return workingLocationRepository
                    .findByWorkingLocationTypeIdAndEducationalDistrictId(
                            typeId,
                            districtId
                    );
        }

        if (usesZone && zone != null && !zone.isBlank()) {
            return workingLocationRepository
                    .findByWorkingLocationTypeNameIgnoreCaseAndEducationalDistrictNameIgnoreCaseAndEducationalZoneNameIgnoreCase(
                            locationType.getName(),
                            district,
                            zone
                    );
        }

        return workingLocationRepository
                .findByWorkingLocationTypeNameIgnoreCaseAndEducationalDistrictNameIgnoreCase(
                        locationType.getName(),
                        district
                );
    }

    // Helper method to find working location type by ID or name
    private WorkingLocationType findWorkingLocationType(String type) {
        Long typeId = parseLongOrNull(type);

        if (typeId != null) {
            return workingLocationTypeRepository.findById(typeId)
                    .orElseThrow(() -> new RuntimeException("Working location type not found"));
        }

        return workingLocationTypeRepository.findByNameIgnoreCase(type)
                .orElseThrow(() -> new RuntimeException("Working location type not found"));
    }

    private Long parseLongOrNull(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }

            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}