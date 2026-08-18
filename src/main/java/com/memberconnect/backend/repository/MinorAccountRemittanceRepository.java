package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MinorAccountRemittance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MinorAccountRemittanceRepository extends JpaRepository<MinorAccountRemittance, Long> {

    List<MinorAccountRemittance> findByMinorAccountNo(String minorAccountNo);

    boolean existsByMinorAccountNoAndRemittanceMonth(String minorAccountNo, String remittanceMonth);

    long countByMinorAccountNoAndRemittanceAmountGreaterThanEqual(String minorAccountNo, Double amount);
}
