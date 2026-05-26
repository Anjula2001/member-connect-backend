package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.RequiredDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequiredDocumentTypeRepository extends JpaRepository<RequiredDocumentType, Long> {
    List<RequiredDocumentType> findByRequestType(String requestType);
}
