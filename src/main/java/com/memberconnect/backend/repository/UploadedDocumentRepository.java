package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.memberconnect.backend.model.UploadedDocument;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {

    @Query("SELECT u FROM UploadedDocument u WHERE u.requestNo = :requestId OR u.requestId = :requestId")
    List<UploadedDocument> findByRequestNo(@Param("requestId") String requestId);

    @Query("SELECT u FROM UploadedDocument u WHERE (u.requestNo = :requestId OR u.requestId = :requestId) AND u.requiredDocumentId = :requiredDocumentId")
    List<UploadedDocument> findByRequestNoAndRequiredDocumentId(@Param("requestId") String requestId, @Param("requiredDocumentId") Long requiredDocumentId);

    @Query("SELECT COUNT(u) > 0 FROM UploadedDocument u WHERE (u.requestNo = :requestId OR u.requestId = :requestId) AND u.requiredDocumentId = :requiredDocumentId")
    boolean existsByRequestNoAndRequiredDocumentId(@Param("requestId") String requestId, @Param("requiredDocumentId") Long requiredDocumentId);

    @Query("SELECT u FROM UploadedDocument u WHERE u.id = :id AND (u.requestNo = :requestId OR u.requestId = :requestId)")
    Optional<UploadedDocument> findByIdAndRequestNo(@Param("id") Long id, @Param("requestId") String requestId);
}