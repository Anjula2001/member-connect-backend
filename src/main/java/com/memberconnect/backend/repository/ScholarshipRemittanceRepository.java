package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.ScholarshipRemittance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScholarshipRemittanceRepository
        extends JpaRepository<ScholarshipRemittance, Long> {

    long countByMember_IdAndRemittedTrueAndRemittanceMonthBetween(
            Long memberId,
            String startMonth,
            String endMonth
    );
    
    boolean existsByMember_IdAndRemittanceMonthAndRemittedTrue(
        Long memberId,
        String remittanceMonth
    );

    boolean existsByMember_MemberIdAndRemittanceMonthAndRemittedTrue(
        String memberId,
        String remittanceMonth
    );
}