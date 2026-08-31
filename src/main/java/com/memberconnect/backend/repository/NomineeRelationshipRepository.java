package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.memberconnect.backend.model.NomineeRelationship;

@Repository
public interface NomineeRelationshipRepository extends JpaRepository<NomineeRelationship, Long> {

    /** Only the relationships the Nominee Change entry should offer. */
    List<NomineeRelationship> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<NomineeRelationship> findByNameIgnoreCase(String name);
}
