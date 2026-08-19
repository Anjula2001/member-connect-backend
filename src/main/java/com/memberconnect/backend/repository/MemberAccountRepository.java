package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.MemberAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberAccountRepository extends JpaRepository<MemberAccount, Long> {
    List<MemberAccount> findByMemberIdOrderByAccountCodeAsc(Long memberId);
    Optional<MemberAccount> findByMemberIdAndAccountCode(Long memberId, RemittanceAccountCode accountCode);
}
