package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.BoardApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoardApprovalListRepository extends JpaRepository<BoardApprovalList, Long> {

	Optional<BoardApprovalList> findByListId(String listId);

	// Used to block deletion of a Board Meeting that still has approvals attached.
	boolean existsByBoardMeetingId(Long boardMeetingId);

	/**
	 * The list view's rows, narrowed to a Board Meeting date period (MR07).
	 *
	 * Previously the screen called findAll() and threw away the rows outside the
	 * period in the browser, so the "Retrieve" button did not actually retrieve
	 * anything narrower - it reloaded the whole table every time.
	 *
	 * Both bounds are optional and independently so: "All" passes neither, and an
	 * open-ended period passes one. Comparing against boardMeetingDate rather than
	 * createdAt is what MR07 means by "Board Meeting date period" - createdAt is when
	 * the list was drawn up, usually weeks earlier.
	 */
	@Query("""
			SELECT l FROM BoardApprovalList l
			WHERE (:from IS NULL OR l.boardMeetingDate >= :from)
			  AND (:to IS NULL OR l.boardMeetingDate <= :to)
			""")
	List<BoardApprovalList> findInMeetingDateRange(
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

	/**
	 * How many applications each list holds, without loading any of them.
	 *
	 * The list panel only ever renders this number, but toDto walked the lazy
	 * applications collection for every row to produce the full id array - one extra
	 * round trip per batch against a database ~80ms away, and every application id of
	 * every list shipped to a screen that shows none of them. SIZE() compiles to a
	 * correlated count, so this is one query for the whole page.
	 */
	@Query("SELECT l.id, SIZE(l.applications) FROM BoardApprovalList l")
	List<Object[]> countApplicationsPerList();

	/**
	 * The applications sitting on a board approval list whose meeting falls in the
	 * given period.
	 *
	 * This is how MR15/16/17's "Board Meeting Date" filter reaches a Member: a member
	 * has no meeting date of its own, only the application it was created from, and
	 * that application is what a board approval list holds. Returning the application
	 * ids lets the member search narrow itself without the two modules sharing a query.
	 */
	@Query("""
			SELECT a.id FROM BoardApprovalList l
			JOIN l.applications a
			WHERE (:from IS NULL OR l.boardMeetingDate >= :from)
			  AND (:to IS NULL OR l.boardMeetingDate <= :to)
			""")
	List<Long> findApplicationIdsInMeetingDateRange(
			@Param("from") LocalDate from,
			@Param("to") LocalDate to);

    /**
     * Counted in the database rather than by loading rows.
     *
     * The dashboard needs a number, not the records behind it; reading the whole
     * table to call .length on it is what this replaces.
     */
    long countByStatusIn(Collection<String> statuses);
}
