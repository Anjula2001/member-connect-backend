package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationRelative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for DeathDonationRelative.
 */
@Repository
public interface DeathDonationRelativeRepository extends JpaRepository<DeathDonationRelative, Long> {

    /** Fetch all relatives belonging to a specific request */
    List<DeathDonationRelative> findByDeathDonationRequestId(Long requestId);
}
