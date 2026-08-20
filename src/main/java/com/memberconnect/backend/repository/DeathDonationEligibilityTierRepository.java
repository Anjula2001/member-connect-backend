package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationEligibilityTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeathDonationEligibilityTierRepository
        extends JpaRepository<DeathDonationEligibilityTier, Long> {

    /**
     * Highest band first, so the first tier whose minMonths is within the
     * member's months remitted is the applicable one.
     */
    List<DeathDonationEligibilityTier> findAllByOrderByMinMonthsDesc();
}
