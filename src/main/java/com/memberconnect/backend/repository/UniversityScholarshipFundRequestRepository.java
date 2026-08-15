package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.UniversityScholarshipFundRequest;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniversityScholarshipFundRequestRepository
        extends JpaRepository<UniversityScholarshipFundRequest, Long> {

    List<UniversityScholarshipFundRequest> findByUniversityScholarshipRequest(UniversityScholarshipRequest request);

    Optional<UniversityScholarshipFundRequest> findByFundRequestId(String fundRequestId);
}
