package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.TerminationApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TerminationApprovalListRepository extends JpaRepository<TerminationApprovalList, Long> {

    Optional<TerminationApprovalList> findByListId(String listId);
}
