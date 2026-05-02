package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.MemberBankAccount;

public interface MemberBankAccountRepository extends JpaRepository<MemberBankAccount, Long> {

    List<MemberBankAccount> findByMemberId(String memberId);

}