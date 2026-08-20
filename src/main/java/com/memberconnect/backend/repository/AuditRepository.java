package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Audit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<Audit, Long> {

    List<Audit> findByModuleNameAndReferenceIdOrderByActionAtAsc(String moduleName, Long referenceId);

    /**
     * A member's history spans two records: the member itself and the application it
     * grew from. The spec requires both on the one Progress tab.
     */
    List<Audit> findByModuleNameInAndReferenceIdInOrderByActionAtAsc(
            List<String> moduleNames, List<Long> referenceIds);

    /**
     * Newest entries across every module, for the dashboard's Recent Activity card.
     *
     * Unlike the two lookups above this is not scoped to one record, so it is paged
     * rather than returning an unbounded list — the audit table grows with every
     * action taken in the system.
     */
    List<Audit> findAllByOrderByActionAtDesc(Pageable pageable);
}
