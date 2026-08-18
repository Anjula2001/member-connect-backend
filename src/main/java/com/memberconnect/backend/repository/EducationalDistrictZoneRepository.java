package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.EducationalDistrictZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EducationalDistrictZoneRepository extends JpaRepository<EducationalDistrictZone, Long> {

    @Query("SELECT DISTINCT e.district FROM EducationalDistrictZone e ORDER BY e.district ASC")
    List<String> findDistinctDistricts();

    @Query("SELECT e.zone FROM EducationalDistrictZone e WHERE LOWER(e.district) = LOWER(:district) ORDER BY e.zone ASC")
    List<String> findZonesByDistrict(@Param("district") String district);

    boolean existsByDistrictIgnoreCaseAndZoneIgnoreCase(String district, String zone);
}
