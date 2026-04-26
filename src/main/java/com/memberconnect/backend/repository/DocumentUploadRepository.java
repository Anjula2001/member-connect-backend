package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentUploadRepository extends JpaRepository<Document, Long> {

    List<Document> findByRequestId(Long requestId);

    boolean existsByRequestIdAndDocumentType(Long requestId, String documentType);
}