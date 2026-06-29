package com.memberconnect.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.TerminationApprovalList;

public interface TerminationApprovalListRepository extends JpaRepository<TerminationApprovalList, Long> {

    Optional<TerminationApprovalList> findByListId(String listId);
}
