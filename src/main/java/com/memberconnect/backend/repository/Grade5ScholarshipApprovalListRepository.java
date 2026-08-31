package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Grade5ScholarshipApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface Grade5ScholarshipApprovalListRepository extends JpaRepository<Grade5ScholarshipApprovalList, Long> {
    Optional<Grade5ScholarshipApprovalList> findByListId(String listId);
    List<Grade5ScholarshipApprovalList> findByType(String type);

	// Used to block deletion of a Board Meeting that still has approvals attached.
	boolean existsByBoardMeetingId(Long boardMeetingId);

    /**
     * Counted in the database rather than by loading rows.
     *
     * The dashboard needs a number, not the records behind it; reading the whole
     * table to call .length on it is what this replaces.
     */
    long countByStatusIn(Collection<String> statuses);
}
