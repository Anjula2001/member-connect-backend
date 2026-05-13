package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for DeathDonationDocument.
 */
@Repository
public interface DeathDonationDocumentRepository extends JpaRepository<DeathDonationDocument, Long> {

    /** Fetch all documents attached to a specific request */
    List<DeathDonationDocument> findByDeathDonationRequestId(Long requestId);
}
