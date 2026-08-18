package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.UniversityScholarshipExamMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UniversityScholarshipExamMasterRepository
        extends JpaRepository<UniversityScholarshipExamMaster, Long> {

    Optional<UniversityScholarshipExamMaster> findByExamYear(String examYear);
}