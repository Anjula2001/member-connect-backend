package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.memberconnect.backend.model.TerminationRequest;

public interface TerminationRequestRepository extends JpaRepository<TerminationRequest, Long> {

    List<TerminationRequest> findByMemberId(String memberId);

    @Query(
        value = """
            SELECT *
            FROM termination_request
            WHERE request_no LIKE CONCAT(:prefix, '%')
            ORDER BY request_no DESC
            LIMIT 1
        """,
        nativeQuery = true
    )
    Optional<TerminationRequest> findLastRequestByPrefix(String prefix);

    Optional<TerminationRequest> findByRequestNo(String requestNo);
}
