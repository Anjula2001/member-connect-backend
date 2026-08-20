package com.memberconnect.backend.repository;

import java.util.List;

import com.memberconnect.backend.model.Audit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Queries over the audit trail.
 *
 * Both orderings are kept deliberately: the Progress tabs read oldest-first, because
 * they render a chronological history, while the profile-change screens read
 * newest-first, because they answer "what happened to this record most recently".
 */
@Repository
public interface AuditRepository extends JpaRepository<Audit, Long> {

    /**
     * Every audit row for one member record, newest first. referenceId is always the
     * Member's database id, never the membership number, so the member's whole history
     * across modules comes back with one lookup.
     */
    List<Audit> findByReferenceIdOrderByActionAtDesc(Long referenceId);

    List<Audit> findByModuleNameAndReferenceIdOrderByActionAtDesc(String moduleName, Long referenceId);

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
