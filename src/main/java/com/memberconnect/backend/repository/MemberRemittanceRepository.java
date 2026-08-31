package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.MemberRemittance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRemittanceRepository extends JpaRepository<MemberRemittance, Long> {
    List<MemberRemittance> findByMemberIdOrderByAccountCodeAsc(Long memberId);
    Optional<MemberRemittance> findByMemberIdAndAccountCode(Long memberId, RemittanceAccountCode accountCode);
}
