package com.memberconnect.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Grade5ScholarshipRequest;

public interface Grade5ScholarshipRepository 
        extends JpaRepository<Grade5ScholarshipRequest, Long> {

    boolean existsByExaminationNumber(String examinationNumber);
}