package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByMemberId(String memberId);
}