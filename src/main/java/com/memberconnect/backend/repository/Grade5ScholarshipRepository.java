package com.memberconnect.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Grade5ScholarshipRequest;

public interface Grade5ScholarshipRepository
        extends JpaRepository<Grade5ScholarshipRequest, Long> {

    boolean existsByExaminationNumber(String examinationNumber);

    Optional<Grade5ScholarshipRequest>
        findTopByRequestNoStartingWithOrderByRequestNoDesc(String prefix);

    Optional<Grade5ScholarshipRequest> findTopByMemberIdOrderByIdDesc(String memberId);
}