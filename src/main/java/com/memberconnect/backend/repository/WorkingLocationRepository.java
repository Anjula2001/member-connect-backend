package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.WorkingLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkingLocationRepository extends JpaRepository<WorkingLocation, Long> {

    List<WorkingLocation> findByWorkingLocationTypeIdAndEducationalDistrictId(
            Long workingLocationTypeId,
            Long educationalDistrictId
    );

    List<WorkingLocation> findByWorkingLocationTypeIdAndEducationalDistrictIdAndEducationalZoneId(
            Long workingLocationTypeId,
            Long educationalDistrictId,
            Long educationalZoneId
    );

    List<WorkingLocation> findByWorkingLocationTypeNameIgnoreCaseAndEducationalDistrictNameIgnoreCase(
            String workingLocationType,
            String district
    );

    List<WorkingLocation> findByWorkingLocationTypeNameIgnoreCaseAndEducationalDistrictNameIgnoreCaseAndEducationalZoneNameIgnoreCase(
            String workingLocationType,
            String district,
            String zone
    );
}