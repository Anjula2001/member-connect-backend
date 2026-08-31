package com.memberconnect.backend.repository;

import java.util.Optional;

import com.memberconnect.backend.model.RemittanceAmountChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RemittanceAmountChangeRepo
        extends JpaRepository<RemittanceAmountChange, Integer>,
                JpaSpecificationExecutor<RemittanceAmountChange> {

    /** Highest request number issued for a prefix, used to derive the next sequence. */
    Optional<RemittanceAmountChange> findFirstByRequestNoStartingWithOrderByRequestNoDesc(String prefix);
}
