package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;

import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.UniversityScholarshipRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityScholarshipRequestRepository
        extends JpaRepository<UniversityScholarshipRequest, Long> {

    boolean existsByExamNumber(String examNumber);

    Optional<UniversityScholarshipRequest> findByUniversityScholarshipRequestID(String universityScholarshipRequestID);

    boolean existsByMember_MemberIdAndStatusAndAcademicYearStartDateBetween(
            String memberId,
            UniversityScholarshipRequestStatus status,
            LocalDate startDate,
            LocalDate endDate
    );


    List<UniversityScholarshipRequest> findByBoardMeeting(BoardMeeting boardMeeting);
}
