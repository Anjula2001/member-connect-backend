package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.UploadDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadDocumentRepository extends JpaRepository<UploadDocument, Long> {
    List<UploadDocument> findByApplicationId(Long applicationId);
}
