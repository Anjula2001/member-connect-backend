package com.memberconnect.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.memberconnect.backend.model.Audit;

@Repository
public interface AuditRepository extends JpaRepository<Audit, Long> {

    /**
     * Every audit row for one member record, newest first. referenceId is always the
     * Member's database id, never the membership number, so the member's whole history
     * across modules comes back with one lookup.
     */
    List<Audit> findByReferenceIdOrderByActionAtDesc(Long referenceId);

    List<Audit> findByModuleNameAndReferenceIdOrderByActionAtDesc(String moduleName, Long referenceId);
}
