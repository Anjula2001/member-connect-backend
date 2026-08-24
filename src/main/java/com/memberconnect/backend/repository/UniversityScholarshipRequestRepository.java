package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;

import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.UniversityScholarshipRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Whether the member already holds a scholarship for the same exam year that was
     * approved inside the given window.
     *
     * processedAt is the moment the board's decision was recorded, so the window is a
     * LocalDateTime pair rather than a pair of dates - a request approved on the
     * closing day of the window is inside it whatever time of day it was processed.
     */
    @Query("select count(request) > 0 from UniversityScholarshipRequest request "
            + "where request.member.memberId = :memberId "
            + "and request.status = :status "
            + "and request.examYear = :examYear "
            + "and request.processedAt between :approvedFrom and :approvedTo")
    boolean existsApprovedForExamYearProcessedBetween(
            @Param("memberId") String memberId,
            @Param("status") UniversityScholarshipRequestStatus status,
            @Param("examYear") String examYear,
            @Param("approvedFrom") LocalDateTime approvedFrom,
            @Param("approvedTo") LocalDateTime approvedTo
    );


    List<UniversityScholarshipRequest> findByBoardMeeting(BoardMeeting boardMeeting);

    List<UniversityScholarshipRequest> findByApprovalListId(String approvalListId);

    List<UniversityScholarshipRequest> findByMember_MemberId(String memberId);

    List<UniversityScholarshipRequest> findByMember_MemberIdAndStatus(
            String memberId,
            UniversityScholarshipRequestStatus status
    );

    long countByMember_MemberIdAndStatus(
            String memberId,
            UniversityScholarshipRequestStatus status
    );
}
