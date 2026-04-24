package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MinorAccount;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MinorAccountRepository extends JpaRepository<MinorAccount, Long> {
    Optional<MinorAccount> findByBirthCertificateNumber(String birthCertificateNumber);
}