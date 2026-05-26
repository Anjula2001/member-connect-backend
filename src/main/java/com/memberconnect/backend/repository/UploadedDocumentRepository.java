package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.UploadedDocument;

import java.util.Optional;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {

    List<UploadedDocument> findByRequestNo(String requestNo);

    List<UploadedDocument> findByRequestNoAndRequiredDocumentId(String requestNo,Long requiredDocumentId);

    boolean existsByRequestNoAndRequiredDocumentId(String requestNo,Long requiredDocumentId);
}