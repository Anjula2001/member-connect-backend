package com.memberconnect.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.ScholarshipConfig;

public interface ScholarshipConfigRepository extends JpaRepository<ScholarshipConfig, Long> {

    Optional<ScholarshipConfig> findByConfigKey(String configKey);
}