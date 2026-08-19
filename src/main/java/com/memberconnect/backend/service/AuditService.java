package com.memberconnect.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memberconnect.backend.dto.AuditDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.Audit;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.AuditRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * Writes and reads the audit trail.
 *
 * Two sets of callers share this service, and both are supported here:
 *
 *  - the Progress/history tabs on the Application and Membership Profile screens
 *    (spec 4.2 and 4.8), which record plain string values and read them back oldest
 *    first as AuditDTOs;
 *  - the profile change requests (Requirement 02, sections 2.1.1 / 3.1.1 / 4.1.1 /
 *    5.1.1), which record before/after field maps serialised to JSON.
 *
 * Recording history must never break the action being recorded, so every write is
 * best-effort: a failure is logged, never thrown. An audit row describes a change that
 * has already been decided; failing to write it must not roll back the approval the
 * user just made.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Module names used as the audit's discriminator. */
    public static final String MODULE_APPLICATION = "MEMBER_APPLICATION";
    public static final String MODULE_MEMBER = "MEMBER";

    /** One per profile-change type, so the trail can be filtered later. */
    public static final String MODULE_BASIC_PROFILE_CHANGE = "PROFILE_CHANGE_BASIC";
    public static final String MODULE_NAME_CHANGE = "PROFILE_CHANGE_NAME";
    public static final String MODULE_NOMINEE_CHANGE = "PROFILE_CHANGE_NOMINEE";
    public static final String MODULE_REMITTANCE_CHANGE = "PROFILE_CHANGE_REMITTANCE";

    private final AuditRepository auditRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditRepository auditRepository,
            MemberRepository memberRepository,
            ObjectMapper objectMapper
    ) {
        this.auditRepository = auditRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    // ── Recording: plain string values ───────────────────────────────────────

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
            // reference_id is NOT NULL. Without a reference there is nothing to hang the
            // row off, and a fabricated id would corrupt the trail for whoever owns that
            // id instead.
            log.warn("Audit row skipped: no reference id. module={}, action={}", moduleName, actionName);
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
            log.error(
                    "Audit row failed to write. module={}, referenceId={}, action={}, cause={}",
                    moduleName, referenceId, actionName, error.toString(), error
            );
        }
    }

    // ── Recording: before/after field maps ───────────────────────────────────

    /**
     * Records a status change on a profile change request against the member it belongs
     * to, resolving the Member's database id from the membership number so callers do
     * not each have to inject MemberRepository to do it.
     *
     * The SRS requires an audit record for every change made to a member's requests,
     * not only for approvals - MMC03/07/16/20's Inactive transition is a change like
     * any other and is audited here.
     */
    public void recordStatusChange(
            String moduleName,
            String memberId,
            String requestNo,
            ApplicationStatus from,
            ApplicationStatus to
    ) {
        Long memberDbId = resolveMemberDbId(memberId);

        recordValues(
                moduleName,
                memberDbId,
                "STATUS_CHANGED",
                Map.of("status", from == null ? "" : from.name()),
                Map.of("status", to == null ? "" : to.name()),
                "Request " + (requestNo == null ? "(no request number)" : requestNo)
        );
    }

    /**
     * Records one change against a member.
     *
     * @param moduleName  one of the MODULE_* constants
     * @param memberDbId  the Member's database id (Audit.referenceId is non-null)
     * @param actionName  what happened, e.g. "APPROVED", "REJECTED", "SET_INACTIVE"
     * @param oldValues   field to previous value; serialised to JSON. May be null.
     * @param newValues   field to new value; serialised to JSON. May be null.
     * @param remarks     free text, e.g. the reject reason. May be null.
     */
    public void recordValues(
            String moduleName,
            Long memberDbId,
            String actionName,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            String remarks
    ) {
        record(moduleName, memberDbId, actionName, toJson(oldValues), toJson(newValues), remarks);
    }

    /**
     * Convenience for the common case: a request whose whole point is a set of
     * before/after field pairs. Pass the same keys in both maps.
     */
    public void recordFieldChanges(
            String moduleName,
            Long memberDbId,
            String actionName,
            Map<String, Object> before,
            Map<String, Object> after,
            String remarks
    ) {
        recordValues(moduleName, memberDbId, actionName, onlyChanged(before, after), onlyChanged(after, before), remarks);
    }

    // ── Reading ──────────────────────────────────────────────────────────────

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * The Member row's primary key for a membership number, or null if the request has
     * no member linked - which is the case for Name and Nominee requests raised before
     * those tables had a memberId column at all.
     */
    private Long resolveMemberDbId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return null;
        }
        return memberRepository.findByMemberId(memberId)
                .map(m -> m.getId())
                .orElse(null);
    }

    /**
     * Keeps just the entries whose counterpart in the other map differs, so the
     * stored JSON is the diff rather than a copy of the whole profile.
     */
    private Map<String, Object> onlyChanged(Map<String, Object> source, Map<String, Object> other) {
        if (source == null) {
            return null;
        }
        Map<String, Object> changed = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Object counterpart = other == null ? null : other.get(key);
            if (value == null ? counterpart != null : !value.equals(counterpart)) {
                changed.put(key, value);
            }
        });
        return changed.isEmpty() ? null : changed;
    }

    private String toJson(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            log.warn("Audit value could not be serialised, storing toString instead: {}", e.toString());
            return values.toString();
        }
    }

    /**
     * The logged-in user, or null for anything the system does on its own
     * (schedulers, seeders). action_by is nullable precisely for that case.
     */
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
