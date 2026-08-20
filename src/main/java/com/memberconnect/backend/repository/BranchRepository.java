package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByBankId(Long bankId);

    /** Branch pickers render this list as-is, so it is ordered in the query. */
    List<Branch> findByBankIdOrderByNameAsc(Long bankId);
}
