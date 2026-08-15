package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.memberconnect.backend.model.DormantApprovalList;

public interface DormantApprovalListRepository extends JpaRepository<DormantApprovalList, Long> {

    Optional<DormantApprovalList> findByListId(String listId);

    @Query("SELECT DISTINCT l FROM DormantApprovalList l LEFT JOIN FETCH l.members ORDER BY l.createdAt DESC")
    List<DormantApprovalList> findAllWithMembers();

    @Query("SELECT l FROM DormantApprovalList l LEFT JOIN FETCH l.members WHERE l.listId = :listId")
    Optional<DormantApprovalList> findByListIdWithMembers(String listId);
}
