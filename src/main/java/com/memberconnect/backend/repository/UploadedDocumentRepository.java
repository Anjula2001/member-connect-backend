package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.UploadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {

    List<UploadedDocument> findByRequestId(String requestId);

    List<UploadedDocument> findByRequestIdAndRequiredDocumentId(String requestId, Long requiredDocumentId);

    Optional<UploadedDocument> findByIdAndRequestId(Long id, String requestId);

    void deleteByRequestId(String requestId);
}
