package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.AuditDTO;
import org.springframework.data.domain.PageRequest;
import com.memberconnect.backend.model.Audit;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Progress/history trail shown on the Application and Membership
 * Profile screens (spec 4.2 and 4.8).
 *
 * Recording history must never break the action being recorded, so every write is
 * best-effort: a failure here is logged, not thrown.
 */
@Service
public class AuditService {

    /** Module names used as the audit's discriminator. */
    public static final String MODULE_APPLICATION = "MEMBER_APPLICATION";
    public static final String MODULE_MEMBER = "MEMBER";

    @Autowired
    private AuditRepository auditRepository;

    @Transactional
    public void record(String moduleName, Long referenceId, String actionName) {
        record(moduleName, referenceId, actionName, null, null, null);
    }

    @Transactional
    public void record(String moduleName, Long referenceId, String actionName, String remarks) {
        record(moduleName, referenceId, actionName, null, null, remarks);
    }

    @Transactional
    public void record(String moduleName, Long referenceId, String actionName,
                       String oldValue, String newValue, String remarks) {
        if (referenceId == null) {
            return;
        }
        try {
            Audit audit = new Audit();
            audit.setModuleName(moduleName);
            audit.setReferenceId(referenceId);
            audit.setActionName(actionName);
            audit.setOldValue(oldValue);
            audit.setNewValue(newValue);
            audit.setRemarks(remarks);
            audit.setActionBy(currentUser());
            auditRepository.save(audit);
        } catch (Exception error) {
            System.err.println("[audit] failed to record " + actionName + ": " + error.getMessage());
        }
    }

    /**
     * The most recent audit entries system-wide, newest first.
     *
     * Backs the dashboard's Recent Activity card, which previously listed the newest
     * member applications and called them "actions across the system". The audit table
     * is the only place that actually records actions from every module.
     *
     * @param limit how many entries to return; clamped to 1..50 so a caller cannot ask
     *              for the whole table.
     */
    public List<AuditDTO> getRecentActivity(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return auditRepository
                .findAllByOrderByActionAtDesc(PageRequest.of(0, safeLimit))
                .stream().map(this::toDto).toList();
    }

    /** History for an application on its own (the Application Progress tab). */
    public List<AuditDTO> getApplicationHistory(Long applicationId) {
        return auditRepository
                .findByModuleNameAndReferenceIdOrderByActionAtAsc(MODULE_APPLICATION, applicationId)
                .stream().map(this::toDto).toList();
    }

    /**
     * History for a member, merged with the application it was created from — the
     * spec requires the application's creation and updates to appear here too.
     */
    public List<AuditDTO> getMemberHistory(Long memberId, Long applicationId) {
        List<String> modules = new ArrayList<>(List.of(MODULE_MEMBER));
        List<Long> refs = new ArrayList<>(List.of(memberId));
        if (applicationId != null) {
            modules.add(MODULE_APPLICATION);
            refs.add(applicationId);
        }
        // Both lists are matched independently, so filter out cross-matches
        // (an application id that happens to equal a member id).
        return auditRepository.findByModuleNameInAndReferenceIdInOrderByActionAtAsc(modules, refs).stream()
                .filter(a -> (MODULE_MEMBER.equals(a.getModuleName()) && a.getReferenceId().equals(memberId))
                        || (MODULE_APPLICATION.equals(a.getModuleName())
                            && applicationId != null && a.getReferenceId().equals(applicationId)))
                .map(this::toDto)
                .toList();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private AuditDTO toDto(Audit a) {
        AuditDTO dto = new AuditDTO();
        dto.setId(a.getId());
        dto.setModuleName(a.getModuleName());
        dto.setReferenceId(a.getReferenceId());
        dto.setActionName(a.getActionName());
        dto.setOldValue(a.getOldValue());
        dto.setNewValue(a.getNewValue());
        dto.setRemarks(a.getRemarks());
        dto.setActionAt(a.getActionAt());
        if (a.getActionBy() != null) {
            dto.setActionBy(a.getActionBy().getFullName() != null
                    ? a.getActionBy().getFullName()
                    : a.getActionBy().getUsername());
        } else {
            dto.setActionBy("System");
        }
        return dto;
    }
}
