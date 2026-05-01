package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.EducationalDistrict;
import com.memberconnect.backend.model.EducationalZone;
import com.memberconnect.backend.model.WorkingLocation;
import com.memberconnect.backend.model.WorkingLocationType;
import com.memberconnect.backend.repository.EducationalDistrictRepository;
import com.memberconnect.backend.repository.EducationalZoneRepository;
import com.memberconnect.backend.repository.WorkingLocationRepository;
import com.memberconnect.backend.repository.WorkingLocationTypeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/masters")
@CrossOrigin(origins = "http://localhost:3000")
public class MastersController {

    private final WorkingLocationTypeRepository workingLocationTypeRepository;
    private final EducationalDistrictRepository educationalDistrictRepository;
    private final EducationalZoneRepository educationalZoneRepository;
    private final WorkingLocationRepository workingLocationRepository;

    public MastersController(
            WorkingLocationTypeRepository workingLocationTypeRepository,
            EducationalDistrictRepository educationalDistrictRepository,
            EducationalZoneRepository educationalZoneRepository,
            WorkingLocationRepository workingLocationRepository
    ) {
        this.workingLocationTypeRepository = workingLocationTypeRepository;
        this.educationalDistrictRepository = educationalDistrictRepository;
        this.educationalZoneRepository = educationalZoneRepository;
        this.workingLocationRepository = workingLocationRepository;
    }

    @GetMapping("/working-location-types")
    public List<WorkingLocationType> getWorkingLocationTypes() {
        return workingLocationTypeRepository.findAll();
    }

    @GetMapping("/districts")
    public List<EducationalDistrict> getDistricts() {
        return educationalDistrictRepository.findAll();
    }

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