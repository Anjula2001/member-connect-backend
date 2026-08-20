package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memberconnect.backend.model.DormantApprovalList;

public interface DormantApprovalListRepository extends JpaRepository<DormantApprovalList, Long> {

    Optional<DormantApprovalList> findByListId(String listId);

    /**
     * Both fetch queries join through entries to the member, so a single round
     * trip carries everything the list screen and the printed sheet need.
     *
     * DISTINCT on both: a fetch join across a collection multiplies the parent
     * row once per child, and this query previously lacked it - a single list
     * came back with its members duplicated.
     */
    @Query("SELECT DISTINCT l FROM DormantApprovalList l "
            + "LEFT JOIN FETCH l.entries e LEFT JOIN FETCH e.member "
            + "ORDER BY l.createdAt DESC")
    List<DormantApprovalList> findAllWithMembers();

    @Query("SELECT DISTINCT l FROM DormantApprovalList l "
            + "LEFT JOIN FETCH l.entries e LEFT JOIN FETCH e.member "
            + "WHERE l.listId = :listId")
    Optional<DormantApprovalList> findByListIdWithMembers(@Param("listId") String listId);

    // Used to block deletion of a Board Meeting that still has approvals attached.
    boolean existsByBoardMeetingId(Long boardMeetingId);

    /**
     * Highest sequence number issued so far, for the next DAL- id. Null when no
     * list has been created yet.
     */
    @Query("SELECT MAX(CAST(SUBSTRING(l.listId, 5) AS int)) FROM DormantApprovalList l "
            + "WHERE l.listId LIKE 'DAL-%'")
    Integer findMaxListSequence();
}
