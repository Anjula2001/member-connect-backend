package com.memberconnect.backend.repository;

import java.util.List;

import com.memberconnect.backend.model.Audit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Queries over the audit trail.
 *
 * Both orderings are kept deliberately: the Progress tabs read oldest-first, because
 * they render a chronological history, while the profile-change screens read
 * newest-first, because they answer "what happened to this record most recently".
 *
 * <h2>Why the read paths carry an entity graph</h2>
 *
 * Audit.actionBy is a LAZY {@code @ManyToOne User}, and every one of these rows is
 * turned into an AuditDTO that reads the user's display name. With
 * {@code spring.jpa.open-in-view=false} the session is already closed by then, so
 * the proxy blew up with "Could not initialize proxy [User#1] - no session" on the
 * Progress tab. Fetching the author in the same query is the explicit fetch that
 * setting asks for, and it costs one join instead of a remote round trip per row.
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

    @EntityGraph(attributePaths = "actionBy")
    List<Audit> findByModuleNameAndReferenceIdOrderByActionAtAsc(String moduleName, Long referenceId);

    /**
     * Every audit row for one module, newest first — the module-wide counterpart of
     * the lookups above, which are all scoped to a single record.
     */
    List<Audit> findByModuleNameOrderByActionAtDesc(String moduleName);

    /**
     * A member's history spans two records: the member itself and the application it
     * grew from. The spec requires both on the one Progress tab.
     */
    @EntityGraph(attributePaths = "actionBy")
    List<Audit> findByModuleNameInAndReferenceIdInOrderByActionAtAsc(
            List<String> moduleNames, List<Long> referenceIds);

    /**
     * Newest entries across every module, for the dashboard's Recent Activity card.
     *
     * Unlike the two lookups above this is not scoped to one record, so it is paged
     * rather than returning an unbounded list — the audit table grows with every
     * action taken in the system.
     */
    @EntityGraph(attributePaths = "actionBy")
    List<Audit> findAllByOrderByActionAtDesc(Pageable pageable);
}
