package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.BoardApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardApprovalListRepository extends JpaRepository<BoardApprovalList, Long> {

	Optional<BoardApprovalList> findByListId(String listId);
}
