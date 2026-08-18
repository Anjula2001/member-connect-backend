package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.MinorSavingsAccount;

public interface MinorSavingsAccountRepository
        extends JpaRepository<MinorSavingsAccount, String> {

    List<MinorSavingsAccount> findByMemberId(String memberId);
    List<MinorSavingsAccount> findByBirthCertificateNo(String birthCertificateNo);
}