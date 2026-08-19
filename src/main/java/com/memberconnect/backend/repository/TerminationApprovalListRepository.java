package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.TerminationApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TerminationApprovalListRepository extends JpaRepository<TerminationApprovalList, Long> {

    Optional<TerminationApprovalList> findByListId(String listId);

    @Query("SELECT DISTINCT tal FROM TerminationApprovalList tal LEFT JOIN FETCH tal.requests")
    List<TerminationApprovalList> findAllWithRequests();

    @Query("SELECT tal FROM TerminationApprovalList tal LEFT JOIN FETCH tal.requests WHERE tal.listId = :listId")
    Optional<TerminationApprovalList> findByListIdWithRequests(String listId);

	// Used to block deletion of a Board Meeting that still has approvals attached.
	boolean existsByBoardMeetingId(Long boardMeetingId);
}
