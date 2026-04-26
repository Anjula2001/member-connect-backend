package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentUploadRepository extends JpaRepository<Document, Long> {

    List<Document> findByRequest_Id(Long requestId);

    boolean existsByRequest_IdAndDocumentType(Long requestId, String documentType);
}