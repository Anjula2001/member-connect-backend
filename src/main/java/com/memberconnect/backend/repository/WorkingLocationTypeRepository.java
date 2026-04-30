package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.WorkingLocationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingLocationTypeRepository extends JpaRepository<WorkingLocationType, Long> {
}