package com.memberconnect.backend.service;

import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.memberconnect.backend.repository.DeathDonationRequestRepository;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

/**
 * Writes and reads the audit trail.
 *
 * Two sets of callers share this service, and both are supported here:
 *
 *  - the Progress/history tabs on the Application and Membership Profile screens
 *    (spec 4.2 and 4.8), which record plain string values and read them back oldest
 *    first as AuditDTOs;
 *  - the profile change requests (Requirement 02, sections 2.1.1 / 3.1.1 / 4.1.1 /
 *    5.1.1), which record before/after field maps serialised to JSON;
 *  - Member Terminations, Record Member Death, Death Donations and Dormant
 *    Membership, which record status transitions against the request's own id.
 *
 * Every write joins the caller's transaction rather than starting its own. An audit row
 * is only true if the action it describes actually committed, so the two must succeed
 * or fail together - a REQUIRES_NEW audit would leave a record of an approval that was
 * subsequently rolled back, which is worse than no record at all.
 *
 * The actor is read from the security context, never from a parameter: who did
 * something is a fact about the request, not an argument a caller may choose.
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

    /** Requirement 04/05 modules: the request's own id is the reference, not the member's. */
    public static final String MODULE_TERMINATION = "MEMBER_TERMINATION";
    public static final String MODULE_TERMINATION_APPROVAL_LIST = "TERMINATION_APPROVAL_LIST";
    public static final String MODULE_MEMBER_DEATH = "MEMBER_DEATH";
    public static final String MODULE_DEATH_DONATION = "DEATH_DONATION";
    public static final String MODULE_DORMANT = "DORMANT_MEMBERSHIP";
    public static final String MODULE_DORMANT_APPROVAL_LIST = "DORMANT_APPROVAL_LIST";

    /** One per profile-change type, so the trail can be filtered later. */
    public static final String MODULE_BASIC_PROFILE_CHANGE = "PROFILE_CHANGE_BASIC";
    public static final String MODULE_NAME_CHANGE = "PROFILE_CHANGE_NAME";
    public static final String MODULE_NOMINEE_CHANGE = "PROFILE_CHANGE_NOMINEE";
    public static final String MODULE_REMITTANCE_CHANGE = "PROFILE_CHANGE_REMITTANCE";

    /**
     * Member Transfers (MMC27-MMC30). Named as a profile change rather than a module of
     * its own because that is what it is: the SRS lists transfers in the same "All
     * Member Profile Change Requests List" as the four above, and MMC30 requires the
     * audit record to be written "against the Member Record" - which is what including
     * it below puts on the member's Progress timeline.
     */
    public static final String MODULE_MEMBER_TRANSFER = "PROFILE_CHANGE_TRANSFER";

    /** The four profile-change modules, as one list for history queries. */
    public static final List<String> PROFILE_CHANGE_MODULES = List.of(
            MODULE_BASIC_PROFILE_CHANGE,
            MODULE_NAME_CHANGE,
            MODULE_NOMINEE_CHANGE,
            MODULE_REMITTANCE_CHANGE,
            MODULE_MEMBER_TRANSFER
    );

    /** Human label per module, used in the Progress timeline's entry titles. */
    public static String labelFor(String moduleName) {
        if (moduleName == null) {
            return "Record";
        }
        return switch (moduleName) {
            case MODULE_BASIC_PROFILE_CHANGE -> "Basic Profile Change Request";
            case MODULE_NAME_CHANGE -> "Name Change Request";
            case MODULE_NOMINEE_CHANGE -> "Nominee Change Request";
            case MODULE_REMITTANCE_CHANGE -> "Remittance Change Request";
            case MODULE_MEMBER_TRANSFER -> "Member Transfer Request";
            case MODULE_APPLICATION -> "Application";
            case MODULE_TERMINATION -> "Termination Request";
            case MODULE_MEMBER_DEATH -> "Member Death Record";
            case MODULE_DEATH_DONATION -> "Death Donation Request";
            case MODULE_DORMANT -> "Dormant Membership";
            default -> "Member";
        };
    }

    private final AuditRepository auditRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    /*
     * Termination, Member Death and Death Donation record their audit entries against
     * their OWN record id, not the member's, so a member's entries in those modules can
     * only be found by first resolving which of those records belong to them. These are
     * repositories, not services, so injecting them here introduces no cycle with the
     * services that call AuditService.
     */
    private final TerminationRequestRepository terminationRequestRepository;
    private final MemberDeathRecordRepository memberDeathRecordRepository;
    private final DeathDonationRequestRepository deathDonationRequestRepository;

    public AuditService(
            AuditRepository auditRepository,
            MemberRepository memberRepository,
            ObjectMapper objectMapper,
            TerminationRequestRepository terminationRequestRepository,
            MemberDeathRecordRepository memberDeathRecordRepository,
            DeathDonationRequestRepository deathDonationRequestRepository
    ) {
        this.auditRepository = auditRepository;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.terminationRequestRepository = terminationRequestRepository;
        this.memberDeathRecordRepository = memberDeathRecordRepository;
        this.deathDonationRequestRepository = deathDonationRequestRepository;
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
            audit.setActionName(decorateAction(moduleName, actionName));
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

        // Plain status names, not a serialised map: the Progress timeline renders
        // oldValue and newValue directly as "FROM -> TO", so a JSON blob would show up
        // verbatim in the UI.
        record(
                moduleName,
                memberDbId,
                labelFor(moduleName) + (to == null ? " Deleted" : " " + humanStatus(to)),
                from == null ? null : from.name(),
                to == null ? null : to.name(),
                describe(requestNo, to)
        );
    }

    /**
     * Convenience overload for a status transition on a record referenced by its own
     * primary key - the shape the termination, member death, death donation and
     * dormant actions take. Distinct from the overload above, which resolves a
     * membership number to the Member row a profile change request hangs off.
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
     * Records a request being raised, so the Progress timeline shows when it was
     * created and by whom - not only what happened to it afterwards.
     */
    public void recordRequestCreated(
            String moduleName,
            String memberId,
            String requestNo,
            ApplicationStatus status
    ) {
        record(
                moduleName,
                resolveMemberDbId(memberId),
                labelFor(moduleName) + " Created",
                null,
                status == null ? null : status.name(),
                describe(requestNo, status)
        );
    }

    /** "NCR-2026-004 · Submitted for approval" - the request id and its status. */
    private String describe(String requestNo, ApplicationStatus status) {
        String id = requestNo == null || requestNo.isBlank() ? "(no request number)" : requestNo;
        return status == null ? id : id + " · " + humanStatus(status);
    }

    private String humanStatus(ApplicationStatus status) {
        if (status == null) {
            return "";
        }
        String words = status.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
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
        // Readable pairs, not JSON: the Progress timeline prints oldValue and newValue
        // verbatim, so a serialised map surfaced as {"fullName":"..."} on screen.
        record(moduleName, memberDbId, actionName, humanValues(oldValues), humanValues(newValues), remarks);
    }

    /** {fullName=A, title=B} becomes "Full Name: A, Title: B". */
    private String humanValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(humanFieldName(key))
               .append(": ")
               .append(value == null || String.valueOf(value).isBlank() ? "(empty)" : value);
        });
        return out.toString();
    }

    /** camelCase field names as the screens show them: "nameAsInPayroll" -> "Name As In Payroll". */
    private String humanFieldName(String field) {
        if (field == null || field.isBlank()) {
            return "";
        }
        String spaced = field.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * Titles a profile-change entry with the request type, so "APPROVED" reads as
     * "Name Change Request Approved" on a timeline that mixes several record types.
     * Entries that already carry a phrase, and the member/application modules, are
     * left as their callers wrote them.
     */
    private String decorateAction(String moduleName, String actionName) {
        if (actionName == null || actionName.isBlank()
                || actionName.contains(" ")
                || !PROFILE_CHANGE_MODULES.contains(moduleName)) {
            return actionName;
        }
        String word = actionName.toLowerCase().replace('_', ' ');
        return labelFor(moduleName) + " " + Character.toUpperCase(word.charAt(0)) + word.substring(1);
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
     * Everything that has happened to this member, across every module that records
     * against them.
     *
     * Audit rows are keyed by (moduleName, referenceId), but referenceId does not mean
     * the same thing in every module:
     *
     *   - MEMBER, the profile-change modules and DORMANT_MEMBERSHIP record against the
     *     member's own row id, so they can be matched directly.
     *   - MEMBER_APPLICATION records against the application the member was created
     *     from, resolved by the caller.
     *   - MEMBER_TERMINATION, MEMBER_DEATH and DEATH_DONATION record against their own
     *     request/record id, so this member's rows in those modules are only findable
     *     by first looking up which of those records are theirs. They were previously
     *     absent from the timeline entirely - a terminated member's Progress tab showed
     *     nothing about the termination.
     *
     * The approval-list modules (TERMINATION_APPROVAL_LIST, DORMANT_APPROVAL_LIST) are
     * deliberately left out: a list spans many members, so its entries describe the list
     * rather than any one member's history.
     *
     * Because referenceId is only meaningful per module, an entry is kept only when its
     * id is one of the ids allowed FOR ITS OWN MODULE. Matching modules and ids
     * independently would let a termination request whose id happens to equal this
     * member's id show up on their timeline.
     */
    public List<AuditDTO> getMemberHistory(Long memberId, Long applicationId) {
        Map<String, Set<Long>> allowed = new LinkedHashMap<>();

        // Modules keyed by the member's own row id.
        allowed.put(MODULE_MEMBER, Set.of(memberId));
        allowed.put(MODULE_DORMANT, Set.of(memberId));
        for (String module : PROFILE_CHANGE_MODULES) {
            allowed.put(module, Set.of(memberId));
        }

        if (applicationId != null) {
            allowed.put(MODULE_APPLICATION, Set.of(applicationId));
        }

        // Modules keyed by their own record id: resolve this member's records first.
        String membershipNo = memberRepository.findById(memberId)
                .map(member -> member.getMemberId())
                .orElse(null);
        if (membershipNo != null && !membershipNo.isBlank()) {
            putIfAny(allowed, MODULE_TERMINATION,
                    terminationRequestRepository.findByMemberId(membershipNo),
                    request -> request.getId());
            putIfAny(allowed, MODULE_MEMBER_DEATH,
                    memberDeathRecordRepository.findByMember_MemberIdOrderByCreatedAtDesc(membershipNo),
                    record -> record.getId());
            putIfAny(allowed, MODULE_DEATH_DONATION,
                    deathDonationRequestRepository.findByMember_MemberIdOrderByRequestedDateDesc(membershipNo),
                    request -> request.getId());
        }

        List<Long> refs = allowed.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .toList();
        if (refs.isEmpty()) {
            return List.of();
        }

        return auditRepository
                .findByModuleNameInAndReferenceIdInOrderByActionAtAsc(
                        List.copyOf(allowed.keySet()), refs)
                .stream()
                .filter(a -> allowed.getOrDefault(a.getModuleName(), Set.of())
                        .contains(a.getReferenceId()))
                .map(this::toDto)
                .toList();
    }

    /** Registers a module's reference ids, skipping it when the member has no records. */
    private <T> void putIfAny(
            Map<String, Set<Long>> allowed,
            String module,
            List<T> records,
            Function<T, Long> idOf
    ) {
        Set<Long> ids = records.stream()
                .map(idOf)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            allowed.put(module, ids);
        }
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
