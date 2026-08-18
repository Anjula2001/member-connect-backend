package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Grade5ScholarshipApprovalList;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface Grade5ScholarshipApprovalListRepository extends JpaRepository<Grade5ScholarshipApprovalList, Long> {
    Optional<Grade5ScholarshipApprovalList> findByListId(String listId);
    List<Grade5ScholarshipApprovalList> findByType(String type);
}
