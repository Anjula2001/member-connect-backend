package com.memberconnect.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memberconnect.backend.model.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByMemberId(String memberId);

    boolean existsByMemberIdAndBalanceGreaterThan(
        String memberId,
        Double amount
    );

    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.memberId = :memberId AND l.balance > 0")
    boolean hasOutstandingLoan(@Param("memberId") String memberId);

    // Bulk counterpart of existsByMemberIdAndBalanceGreaterThan(memberId, 0.0):
    // answers the same question for a whole page of rows in one round trip.
    @Query("SELECT DISTINCT l.memberId FROM Loan l WHERE l.memberId IN :memberIds AND l.balance > 0")
    List<String> findMemberIdsWithPositiveBalance(@Param("memberIds") Collection<String> memberIds);
}