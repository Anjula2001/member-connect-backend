package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.TerminationApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TerminationApprovalListRepository extends JpaRepository<TerminationApprovalList, Long> {

    Optional<TerminationApprovalList> findByListId(String listId);

    /**
     * The list view's rows, narrowed to a Board Meeting date period.
     *
     * Deliberately WITHOUT the JOIN FETCH above: the list panel shows a count, not the
     * requests, so fetching them was pulling every termination request of every list
     * across the wire to render a number. Pair with countRequestsPerList().
     *
     * Both bounds are optional and independent - "All" passes neither.
     */
    @Query("""
            SELECT tal FROM TerminationApprovalList tal
            WHERE (:from IS NULL OR tal.boardMeetingDate >= :from)
              AND (:to IS NULL OR tal.boardMeetingDate <= :to)
            """)
    List<TerminationApprovalList> findInMeetingDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** How many requests each list holds, without loading any of them. */
    @Query("SELECT tal.id, SIZE(tal.requests) FROM TerminationApprovalList tal")
    List<Object[]> countRequestsPerList();

    @Query("SELECT tal FROM TerminationApprovalList tal LEFT JOIN FETCH tal.requests WHERE tal.listId = :listId")
    Optional<TerminationApprovalList> findByListIdWithRequests(String listId);

	// Used to block deletion of a Board Meeting that still has approvals attached.
	boolean existsByBoardMeetingId(Long boardMeetingId);
}
