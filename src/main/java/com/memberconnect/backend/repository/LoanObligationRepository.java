package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.LoanObligation;

public interface LoanObligationRepository extends JpaRepository<LoanObligation, Long> {
    List<LoanObligation> findByMemberId(String memberId);
}