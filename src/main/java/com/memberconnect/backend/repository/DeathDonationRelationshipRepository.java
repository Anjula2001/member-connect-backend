package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.DeathDonationRelationship;

public interface DeathDonationRelationshipRepository
        extends JpaRepository<DeathDonationRelationship, Long> {

    List<DeathDonationRelationship> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<DeathDonationRelationship> findByNameIgnoreCase(String name);

    Optional<DeathDonationRelationship> findByCodeIgnoreCase(String code);
}
