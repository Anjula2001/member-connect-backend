package com.memberconnect.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.model.Audit;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.AuditRepository;

/**
 * Writes the audit trail the SRS requires for create, edit, approve and reject.
 *
 * The {@link Audit} entity has existed since the first schema but had no writer
 * anywhere in the codebase, so the table stayed empty and none of those actions
 * were attributable. This is that writer.
 *
 * Deliberately joins the caller's transaction rather than starting its own. An
 * audit row is only true if the action it describes actually committed, so the
 * two must succeed or fail together - a REQUIRES_NEW audit would leave a record
 * of an approval that was subsequently rolled back, which is worse than no
 * record at all.
 *
 * The actor is read from the security context, never from a parameter: who did
 * something is a fact about the request, not an argument a caller may choose.
 */
@Service
@Transactional
public class AuditService {

    public static final String MODULE_TERMINATION = "MEMBER_TERMINATION";
    public static final String MODULE_TERMINATION_APPROVAL_LIST = "TERMINATION_APPROVAL_LIST";
    public static final String MODULE_MEMBER_DEATH = "MEMBER_DEATH";
    public static final String MODULE_DEATH_DONATION = "DEATH_DONATION";
    public static final String MODULE_DORMANT = "DORMANT_MEMBERSHIP";
    public static final String MODULE_DORMANT_APPROVAL_LIST = "DORMANT_APPROVAL_LIST";

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Records one action.
     *
     * @param moduleName  which module the action belongs to
     * @param referenceId the affected row's primary key (non-null: the column is
     *                    NOT NULL, and an audit row that does not say what it is
     *                    about is not worth writing)
     * @param actionName  what happened, e.g. CREATE / UPDATE / APPROVE / REJECT
     * @param oldValue    state before, or null where the action has no prior state
     * @param newValue    state after, or null where the action removes something
     * @param remarks     free text giving the action its context - the member id,
     *                    the reject reason, the list the request sat on
     */
    public void record(
            String moduleName,
            Long referenceId,
            String actionName,
            String oldValue,
            String newValue,
            String remarks
    ) {
        if (referenceId == null) {
            // Nothing sensible to attach the row to. Skipped rather than thrown:
            // failing to audit must not fail the action being audited.
            return;
        }

        Audit audit = new Audit();
        audit.setModuleName(moduleName);
        audit.setReferenceId(referenceId);
        audit.setActionName(actionName);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setRemarks(remarks);
        audit.setActionBy(currentUser());

        auditRepository.save(audit);
    }

    /**
     * Convenience overload for a status transition, which is the shape most of the
     * termination actions take.
     */
    public void recordStatusChange(
            String moduleName,
            Long referenceId,
            String actionName,
            Object oldStatus,
            Object newStatus,
            String remarks
    ) {
        record(
                moduleName,
                referenceId,
                actionName,
                oldStatus != null ? String.valueOf(oldStatus) : null,
                newStatus != null ? String.valueOf(newStatus) : null,
                remarks
        );
    }

    /**
     * The authenticated principal is the User entity itself (see JwtFilter), so it
     * can be attached directly. Null for unauthenticated callers - the Finance
     * callback authenticates as a service user, but direct service-level tests do
     * not, and an audit row with no actor still beats losing the action entirely.
     */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }

        return null;
    }
}
