package com.memberconnect.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memberconnect.backend.model.LoanObligation;

public interface LoanObligationRepository extends JpaRepository<LoanObligation, Long> {
    List<LoanObligation> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);

    @Query("SELECT COUNT(o) > 0 FROM LoanObligation o WHERE o.memberId = :memberId")
    boolean hasLoanObligations(@Param("memberId") String memberId);

    // Bulk counterpart of existsByMemberId, for list screens.
    @Query("SELECT DISTINCT o.memberId FROM LoanObligation o WHERE o.memberId IN :memberIds")
    List<String> findMemberIdsWithObligations(@Param("memberIds") Collection<String> memberIds);
}