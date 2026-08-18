package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.RequiredDocument;

public interface RequiredDocumentRepository extends JpaRepository<RequiredDocument, Long> {
    List<RequiredDocument> findByApplicationTypeIn(List<String> applicationTypes);
    List<RequiredDocument> findByApplicationType(String applicationType);
}