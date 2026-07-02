package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeathDonationDocumentRepository extends JpaRepository<DeathDonationDocument, Long> {

    List<DeathDonationDocument> findByRequest_RequestNo(String requestNo);

    List<DeathDonationDocument> findByRequest_RequestNoAndDocumentType(String requestNo, String documentType);

    boolean existsByRequest_RequestNoAndDocumentType(String requestNo, String documentType);
}
