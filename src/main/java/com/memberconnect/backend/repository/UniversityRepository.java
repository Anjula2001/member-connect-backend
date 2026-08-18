package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.University;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {
}