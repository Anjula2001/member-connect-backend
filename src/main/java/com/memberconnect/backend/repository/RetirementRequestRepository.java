package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.RetirementRequest;

public interface RetirementRequestRepository extends JpaRepository<RetirementRequest, Long> {
    List<RetirementRequest> findByMemberId(String memberId);
}