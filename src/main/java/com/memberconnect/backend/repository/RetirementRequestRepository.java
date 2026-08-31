package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.memberconnect.backend.enums.RetirementRequestStatus;
import com.memberconnect.backend.model.RetirementRequest;

public interface RetirementRequestRepository extends JpaRepository<RetirementRequest, Long> {

    List<RetirementRequest> findByMemberId(String memberId);

    boolean existsByMemberIdAndStatusNot(
            String memberId,
            RetirementRequestStatus status
    );

    @Query(
        value = """
            SELECT *
            FROM retirement_request
            WHERE request_no LIKE CONCAT(:prefix, '%')
            ORDER BY request_no DESC
            LIMIT 1
        """,
        nativeQuery = true
    )
    Optional<RetirementRequest> findLastRequestByPrefix(String prefix);

    Optional<RetirementRequest> findByRequestNo(String requestNo);

}