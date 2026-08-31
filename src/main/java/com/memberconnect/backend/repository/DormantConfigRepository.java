package com.memberconnect.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.DormantConfig;

public interface DormantConfigRepository extends JpaRepository<DormantConfig, Long> {
}
