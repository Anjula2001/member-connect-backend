package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.WorkingLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkingLocationRepository extends JpaRepository<WorkingLocation, Long> {

    @Query("SELECT wl FROM WorkingLocation wl WHERE wl.workingLocationType.id = :typeId AND wl.educationalDistrict.id = :districtId")
    List<WorkingLocation> findByWorkingLocationTypeIdAndEducationalDistrictId(
            @Param("typeId") Long workingLocationTypeId,
            @Param("districtId") Long educationalDistrictId
    );

    @Query("SELECT wl FROM WorkingLocation wl WHERE wl.workingLocationType.id = :typeId AND wl.educationalDistrict.id = :districtId AND wl.educationalZone.id = :zoneId")
    List<WorkingLocation> findByWorkingLocationTypeIdAndEducationalDistrictIdAndEducationalZoneId(
            @Param("typeId") Long workingLocationTypeId,
            @Param("districtId") Long educationalDistrictId,
            @Param("zoneId") Long educationalZoneId
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