package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.UploadedDocument;

import java.util.Optional;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {
    List<UploadedDocument> findByRequestId(String requestId);
    
    List<UploadedDocument> findByRequestIdAndRequiredDocumentId(
        String requestId,
        Long requiredDocumentId
    );

    boolean existsByRequestIdAndRequiredDocumentId(String requestId, Long requiredDocumentId);
    
    Optional<UploadedDocument> findByIdAndRequestId(Long id, String requestId);
}