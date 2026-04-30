package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.WorkingLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingLocationRepository extends JpaRepository<WorkingLocation, Long> {
    
}