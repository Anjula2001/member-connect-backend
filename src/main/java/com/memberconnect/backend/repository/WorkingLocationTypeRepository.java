package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.WorkingLocationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkingLocationTypeRepository extends JpaRepository<WorkingLocationType, Long> {

    Optional<WorkingLocationType> findByNameIgnoreCase(String name);
}