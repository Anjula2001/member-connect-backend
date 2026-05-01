package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.EducationalZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationalZoneRepository extends JpaRepository<EducationalZone, Long> {

    List<EducationalZone> findByEducationalDistrictId(Long districtId);

    List<EducationalZone> findByEducationalDistrictNameIgnoreCase(String districtName);
}