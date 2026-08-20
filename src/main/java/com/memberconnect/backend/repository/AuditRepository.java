package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Audit;

public interface AuditRepository extends JpaRepository<Audit, Long> {

    List<Audit> findByModuleNameOrderByActionAtDesc(String moduleName);

    List<Audit> findByModuleNameAndReferenceIdOrderByActionAtDesc(String moduleName, Long referenceId);
}
