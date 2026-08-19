package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.RemittanceAccountCode;
import com.memberconnect.backend.model.RemittanceMasterAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RemittanceMasterRepository extends JpaRepository<RemittanceMasterAccount, Long> {
    Optional<RemittanceMasterAccount> findByAccountCode(RemittanceAccountCode accountCode);
    List<RemittanceMasterAccount> findAllByOrderByDisplayOrderAsc();
    List<RemittanceMasterAccount> findByActiveTrueOrderByDisplayOrderAsc();
}
