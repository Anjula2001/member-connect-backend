package com.memberconnect.backend.repository;

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
}