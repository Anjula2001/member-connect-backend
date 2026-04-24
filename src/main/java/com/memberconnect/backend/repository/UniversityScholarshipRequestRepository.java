package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.UniversityScholarshipRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityScholarshipRequestRepository
        extends JpaRepository<UniversityScholarshipRequest, Long> {

    boolean existsByExamNumber(String examNumber);
}
