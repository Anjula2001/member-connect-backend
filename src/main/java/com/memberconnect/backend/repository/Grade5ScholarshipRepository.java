package com.memberconnect.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Grade5ScholarshipRequest;

public interface Grade5ScholarshipRepository
        extends JpaRepository<Grade5ScholarshipRequest, Long> {

    boolean existsByExaminationNumber(String examinationNumber);

    boolean existsByExaminationNumberAndRequestNoNot(
            String examinationNumber,
            String requestNo
    );

    boolean existsByBirthCertificateNumber(String birthCertificateNumber);

    boolean existsByBirthCertificateNumberAndRequestNoNot(
            String birthCertificateNumber,
            String requestNo
    );

    Optional<Grade5ScholarshipRequest>
        findTopByRequestNoStartingWithOrderByRequestNoDesc(String prefix);

    Optional<Grade5ScholarshipRequest> findTopByMemberIdOrderByIdDesc(String memberId);

    Optional<Grade5ScholarshipRequest> findByRequestNo(String requestNo);
}