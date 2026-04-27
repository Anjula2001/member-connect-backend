package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.ScholarshipMonthSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScholarshipMonthSettlementRepository
        extends JpaRepository<ScholarshipMonthSettlement, Long> {

    boolean existsByMember_IdAndSettlementMonthAndSettledTrue(
            Long memberId,
            String settlementMonth
    );
}