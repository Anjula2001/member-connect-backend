package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for DeathDonationRequest.
 * All basic CRUD methods (save, findById, findAll, delete) are inherited automatically.
 */
@Repository
public interface DeathDonationRequestRepository extends JpaRepository<DeathDonationRequest, Long> {

    /** Find a request by its human-readable requestId (e.g. "DDR-1714901234567") */
    Optional<DeathDonationRequest> findByRequestId(String requestId);

    /** Find all requests belonging to a particular member */
    List<DeathDonationRequest> findByMemberId(Long memberId);
}
