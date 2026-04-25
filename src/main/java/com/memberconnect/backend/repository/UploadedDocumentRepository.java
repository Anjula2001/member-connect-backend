package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.UploadedDocument;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {
    List<UploadedDocument> findByRequestId(Long requestId);

    boolean existsByRequestIdAndRequiredDocumentId(Long requestId, Long requiredDocumentId);
}