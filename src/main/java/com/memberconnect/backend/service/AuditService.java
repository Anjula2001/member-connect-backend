package com.memberconnect.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.Audit;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.AuditRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * Writes the audit trail the SRS requires against the Member Record for every
 * profile change (Requirement 02, sections 2.1.1 / 3.1.1 / 4.1.1 / 5.1.1).
 *
 * The Audit entity has existed since the schema was first laid out but had no
 * repository, so nothing was ever written to it. This service is the writer.
 *
 * Like NotificationService, it never throws. An audit row is a record of a change
 * that has already been decided; failing to write it must not roll back the
 * approval the user just made. Every failure is logged with enough context to
 * reconstruct the missing row by hand.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Module names, one per profile-change type, so the trail can be filtered later. */
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

        record(
                moduleName,
                memberDbId,
                "STATUS_CHANGED",
                Map.of("status", from == null ? "" : from.name()),
                Map.of("status", to == null ? "" : to.name()),
                "Request " + (requestNo == null ? "(no request number)" : requestNo)
        );
    }

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
     * Records one change against a member.
     *
     * @param moduleName  one of the MODULE_* constants
     * @param memberDbId  the Member's database id (Audit.referenceId is non-null)
     * @param actionName  what happened, e.g. "APPROVED", "REJECTED", "SET_INACTIVE"
     * @param oldValues   field to previous value; serialised to JSON. May be null.
     * @param newValues   field to new value; serialised to JSON. May be null.
     * @param remarks     free text, e.g. the reject reason. May be null.
     */
    public void record(
            String moduleName,
            Long memberDbId,
            String actionName,
            Map<String, Object> oldValues,
            Map<String, Object> newValues,
            String remarks
    ) {
        if (memberDbId == null) {
            // reference_id is NOT NULL. Without a member there is nothing to hang the
            // row off, and a fabricated id would corrupt the trail for whoever owns
            // that id instead.
            log.warn(
                    "Audit row skipped: no member id. module={}, action={}",
                    moduleName, actionName
            );
            return;
        }

        try {
            Audit audit = new Audit();
            audit.setModuleName(moduleName);
            audit.setReferenceId(memberDbId);
            audit.setActionName(actionName);
            audit.setOldValue(toJson(oldValues));
            audit.setNewValue(toJson(newValues));
            audit.setRemarks(remarks);
            audit.setActionBy(currentUser());

            auditRepository.save(audit);
        } catch (Exception e) {
            log.error(
                    "Audit row failed to write. module={}, memberDbId={}, action={}, cause={}",
                    moduleName, memberDbId, actionName, e.toString(), e
            );
        }
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
        record(moduleName, memberDbId, actionName, onlyChanged(before, after), onlyChanged(after, before), remarks);
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
}
