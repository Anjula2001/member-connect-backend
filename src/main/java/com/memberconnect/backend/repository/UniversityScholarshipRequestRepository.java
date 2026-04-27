package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;
import com.memberconnect.backend.model.UniversityScholarshipRequest;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityScholarshipRequestRepository
        extends JpaRepository<UniversityScholarshipRequest, Long> {

    boolean existsByExamNumber(String examNumber);

    boolean existsByMember_MemberIdAndStatusAndAcademicYearStartDateBetween(
            String memberId,
            UniversityScholarshipRequestStatus status,
            LocalDate startDate,
            LocalDate endDate
    );
}
