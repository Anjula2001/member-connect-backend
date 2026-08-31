package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.DormantApprovalListDTO;
import com.memberconnect.backend.dto.DormantConfigDTO;
import com.memberconnect.backend.dto.DormantMemberDTO;
import com.memberconnect.backend.dto.DormantMemberDecisionDTO;
import com.memberconnect.backend.dto.FinanceDormantHandoffDTO;
import com.memberconnect.backend.enums.DormantApprovalListStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.event.DormantInactivationApprovedEvent;
import com.memberconnect.backend.event.DormantSelectedEvent;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.DormantApprovalList;
import com.memberconnect.backend.model.DormantApprovalListMember;
import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.DormantApprovalListRepository;
import com.memberconnect.backend.repository.DormantConfigRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * Inactivating Dormant Membership Profiles (SRS Requirement 05, section 4).
 */
@Service
@Transactional
public class DormantMembershipService {

    private static final List<MemberStatus> DORMANT_STATUSES = List.of(
            MemberStatus.SELECTED_FOR_DORMANT,
            MemberStatus.SENT_FOR_DORMANT_APPROVAL,
            MemberStatus.INACTIVE_DORMANT
    );

    // ---------------------------------------------------------------------
    // Authorization
    //
    // Twins of the SpEL constants on DormantMembershipController. Both layers
    // are deliberate: the annotation is the boundary, and these are what catch
    // an endpoint somebody adds later without one. Kept in the same order and
    // with the same names so a change to either is obvious against the other.
    // ---------------------------------------------------------------------

    /** MMD12. District Office is read-only and pinned to its own district below. */
    private static final Set<Role> READ_ROLES = Set.of(
            Role.HEAD_OFFICE, Role.BOARD_SECRETARY, Role.DISTRICT_OFFICE, Role.SUPER_ADMIN);

    /** The dormancy period decides who gets inactivated - membership policy. */
    private static final Set<Role> CONFIG_WRITE_ROLES = Set.of(Role.SUPER_ADMIN);

    /** MMD11: "Authorized Head Office System User". */
    private static final Set<Role> IDENTIFICATION_ROLES = Set.of(Role.HEAD_OFFICE, Role.SUPER_ADMIN);

    /** MMD13/14/16/17/18: the board half. */
    private static final Set<Role> BOARD_ROLES = Set.of(
            Role.HEAD_OFFICE, Role.BOARD_SECRETARY, Role.SUPER_ADMIN);

    /** MMD15: "delete privileges", narrower than ordinary Head Office access. */
    private static final Set<Role> DELETE_ROLES = Set.of(Role.BOARD_SECRETARY, Role.SUPER_ADMIN);

    private final MemberRepository memberRepository;
    private final DormantConfigRepository configRepository;
    private final DormantApprovalListRepository approvalListRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final LoanObligationRepository obligationRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberStatusHistoryService memberStatusHistoryService;

    public DormantMembershipService(
            MemberRepository memberRepository,
            DormantConfigRepository configRepository,
            DormantApprovalListRepository approvalListRepository,
            BoardmeetingRepository boardMeetingRepository,
            LoanObligationRepository obligationRepository,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            MemberStatusHistoryService memberStatusHistoryService
    ) {
        this.memberRepository = memberRepository;
        this.configRepository = configRepository;
        this.approvalListRepository = approvalListRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.obligationRepository = obligationRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.memberStatusHistoryService = memberStatusHistoryService;
    }

    // ---------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------

    public DormantConfig getConfig() {
        return configRepository.findAll().stream().findFirst()
                .orElseGet(() -> configRepository.save(new DormantConfig()));
    }

    public void seedDefaultConfigIfEmpty() {
        if (configRepository.count() == 0) {
            configRepository.save(new DormantConfig());
            System.out.println("Seeded default dormant configuration.");
        }
    }

    public DormantConfigDTO getConfigDto() {
        assertMayRead();
        return toConfigDto(getConfig());
    }

    public DormantConfigDTO updateConfig(DormantConfigDTO dto) {
        assertMayConfigure();
        DormantConfig config = getConfig();

        if (dto.getDormantPeriodMonths() != null) {
            if (dto.getDormantPeriodMonths() < 1) {
                throw badRequest("Dormant period must be at least 1 month");
            }
            config.setDormantPeriodMonths(dto.getDormantPeriodMonths());
        }
        if (dto.getScheduleDayOfMonth() != null) {
            // Capped at 28 so the run cannot be scheduled for a day some months
            // do not have.
            if (dto.getScheduleDayOfMonth() < 1 || dto.getScheduleDayOfMonth() > 28) {
                throw badRequest("Schedule day of month must be between 1 and 28");
            }
            config.setScheduleDayOfMonth(dto.getScheduleDayOfMonth());
        }
        if (dto.getScheduleHour() != null) {
            if (dto.getScheduleHour() < 0 || dto.getScheduleHour() > 23) {
                throw badRequest("Schedule hour must be between 0 and 23");
            }
            config.setScheduleHour(dto.getScheduleHour());
        }
        if (dto.getScheduleMinute() != null) {
            if (dto.getScheduleMinute() < 0 || dto.getScheduleMinute() > 59) {
                throw badRequest("Schedule minute must be between 0 and 59");
            }
            config.setScheduleMinute(dto.getScheduleMinute());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }

        return toConfigDto(configRepository.save(config));
    }

    // ---------------------------------------------------------------------
    // Identification process (MMD10 / MMD11)
    // ---------------------------------------------------------------------

    /**
     * The date a member's account was last touched, for dormancy purposes.
     *
     * Falls back to the membership start date, which is the earliest defensible
     * evidence of activity for a member who has none recorded. The old code
     * guarded both loops on lastActivityDate being non-null, so a member with no
     * activity at all - arguably the most dormant case there is - could never be
     * flagged. Null now means "nothing since they joined", not "exempt".
     */
    private LocalDate activityAnchor(Member member) {
        return member.getLastActivityDate() != null
                ? member.getLastActivityDate()
                : member.getMembershipStartDate();
    }

    /**
     * Runs the dormant identification process. Returns [newlySelected, cleared].
     */
    public int[] runIdentification() {
        assertMayRunIdentification();

        DormantConfig config = getConfig();
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusMonths(config.getDormantPeriodMonths());

        // Re-validate members already flagged: if their account has since been
        // updated, remove the dormant flag and set them back to Active.
        int cleared = 0;
        List<Member> flagged = memberRepository.findByStatusIn(List.of(
                MemberStatus.SELECTED_FOR_DORMANT,
                MemberStatus.SENT_FOR_DORMANT_APPROVAL));

        for (Member member : flagged) {
            LocalDate anchor = activityAnchor(member);
            if (anchor != null && !anchor.isBefore(cutoff)) {
                MemberStatus statusBeforeClear = member.getStatus();
                member.setStatus(MemberStatus.ACTIVE);
                member.setDormantSelectionDate(null);
                memberRepository.save(member);
                memberStatusHistoryService.record(member, statusBeforeClear, member.getStatus(),
                        anchor, "DORMANT_FLAG_CLEARED",
                        "Recent account activity cleared the dormant flag");
                cleared++;
            }
        }

        // Flag Active members whose accounts have not been updated for the period.
        int selected = 0;
        List<Member> actives = memberRepository.findByStatus(MemberStatus.ACTIVE);

        for (Member member : actives) {
            LocalDate anchor = activityAnchor(member);
            if (anchor == null || anchor.isBefore(cutoff)) {
                MemberStatus statusBeforeSelect = member.getStatus();
                member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
                member.setDormantSelectionDate(today);
                memberRepository.save(member);
                memberStatusHistoryService.record(member, statusBeforeSelect, member.getStatus(),
                        today, "DORMANT_SELECTED",
                        "Selected by the dormant identification run");
                selected++;

                eventPublisher.publishEvent(new DormantSelectedEvent(
                        member.getMemberId(), config.getDormantPeriodMonths()));
            }
        }

        // Recorded so the scheduler can tell a run it missed from one it already
        // did, and so the screen can show when the last run happened.
        config.setLastRunOn(today);
        config.setLastRunSelectedCount(selected);
        config.setLastRunClearedCount(cleared);
        configRepository.save(config);

        System.out.println("Dormant identification complete. Selected=" + selected + ", cleared=" + cleared);
        return new int[]{selected, cleared};
    }

    // ---------------------------------------------------------------------
    // Viewing / searching (MMD12)
    // ---------------------------------------------------------------------

    /**
     * The Location filter options (MMD12). Scoped like the results themselves:
     * a district user is offered their own district and nothing else, otherwise
     * the dropdown leaks the full district list even though the rows below it
     * are filtered.
     */
    public List<String> getLocations() {
        assertMayRead();
        Set<String> visible = resolveVisibleLocations(null);

        return memberRepository.findAll().stream()
                .filter(member -> withinVisibleLocations(member, visible))
                .map(this::districtOf)
                .filter(loc -> loc != null && !loc.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getMemberTypes() {
        assertMayRead();
        return memberRepository.findAll().stream()
                .map(Member::getMemberType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<DormantMemberDTO> searchDormantMembers(
            List<String> locations,
            String memberType,
            String dateFilter,
            LocalDate fromDate,
            LocalDate toDate,
            List<String> statuses,
            String search,
            String sortBy,
            String sortOrder
    ) {
        assertMayRead();

        List<MemberStatus> statusFilter = resolveStatuses(statuses);
        List<Member> members = memberRepository.findByStatusIn(statusFilter);

        // Null means unrestricted. For a District Office caller this ignores
        // whatever locations were requested and pins them to their own - the
        // screen never offers the others, which is exactly why the check has to
        // live here rather than in the UI.
        Set<String> visible = resolveVisibleLocations(locations);
        boolean allTypes = memberType == null || memberType.isBlank() || "All".equalsIgnoreCase(memberType);
        String searchTerm = search == null ? "" : search.trim().toLowerCase();

        LocalDate[] range = resolveDateRange(dateFilter, fromDate, toDate);
        LocalDate rangeStart = range[0];
        LocalDate rangeEnd = range[1];

        List<Member> matched = members.stream()
                .filter(m -> withinVisibleLocations(m, visible))
                .filter(m -> allTypes || (m.getMemberType() != null
                        && m.getMemberType().equalsIgnoreCase(memberType)))
                .filter(m -> matchesDateRange(m.getDormantSelectionDate(), rangeStart, rangeEnd))
                .filter(m -> matchesSearch(m, searchTerm))
                .toList();

        List<DormantMemberDTO> result = new ArrayList<>(mapMembers(matched, null));
        sort(result, sortBy, sortOrder);
        return result;
    }

    // ---------------------------------------------------------------------
    // Approval list + board integration (MMD13 - MMD18)
    // ---------------------------------------------------------------------

    public DormantApprovalListDTO createApprovalList(DormantApprovalListDTO dto) {
        assertMayManageLists();

        if (dto.getMemberIds() == null || dto.getMemberIds().isEmpty()) {
            throw badRequest("No dormant members selected for the approval list");
        }
        if (dto.getBoardMeetingId() == null) {
            throw badRequest("Board meeting is required");
        }

        BoardMeeting boardMeeting = boardMeetingRepository.findById(dto.getBoardMeetingId())
                .orElseThrow(() -> notFound("Board Meeting not found"));

        DormantApprovalList entity = new DormantApprovalList();
        entity.setListId(nextListId());
        entity.setBoardMeetingId(boardMeeting.getId());
        entity.setBoardMeetingDate(boardMeeting.getScheduledDate());
        entity.setStatus(DormantApprovalListStatus.CREATED);
        entity.setCreatedAt(LocalDateTime.now());

        // Duplicates in the payload would otherwise become two entries for one
        // member and surface as a raw constraint violation.
        List<String> memberIds = dto.getMemberIds().stream().distinct().toList();
        Set<String> withObligations = obligationsFor(memberIds);

        for (String memberId : memberIds) {
            Member member = memberRepository.findByMemberId(memberId)
                    .orElseThrow(() -> notFound("Member not found: " + memberId));

            if (member.getStatus() != MemberStatus.SELECTED_FOR_DORMANT) {
                throw conflict(
                        "Only 'Selected for Dormant' members can be added to an approval list: " + memberId);
            }
            if (withObligations.contains(memberId)) {
                throw conflict(
                        "Member has indirect loan obligations and cannot be inactivated: " + memberId);
            }

            // Remembered now so deleting the list, or the board rejecting the
            // member, can put them back where they were rather than assuming.
            MemberStatus previousStatus = member.getStatus();

            DormantApprovalListMember entry = new DormantApprovalListMember();
            entry.setApprovalList(entity);
            entry.setMember(member);
            entry.setMemberNo(member.getMemberId());
            entry.setPreviousStatus(previousStatus);
            entity.getEntries().add(entry);

            member.setStatus(MemberStatus.SENT_FOR_DORMANT_APPROVAL);
            memberRepository.save(member);
            memberStatusHistoryService.record(member, previousStatus, member.getStatus(),
                    null, "DORMANT_SENT_FOR_APPROVAL",
                    "Added to dormant approval list " + entity.getListId());

            auditService.recordStatusChange(
                    AuditService.MODULE_DORMANT,
                    member.getId(),
                    "ADDED_TO_APPROVAL_LIST",
                    previousStatus,
                    member.getStatus(),
                    "Added to dormant approval list " + entity.getListId()
            );
        }

        DormantApprovalList saved = approvalListRepository.save(entity);

        auditService.record(
                AuditService.MODULE_DORMANT_APPROVAL_LIST,
                saved.getId(),
                "CREATE_LIST",
                null,
                "listId=" + saved.getListId()
                        + ", boardMeetingId=" + saved.getBoardMeetingId()
                        + ", members=" + saved.getEntries().size(),
                "Inactivation approval list assembled for the board meeting on "
                        + saved.getBoardMeetingDate()
        );

        return toDto(saved);
    }

    public List<DormantApprovalListDTO> getAllApprovalLists() {
        assertMayManageLists();
        return approvalListRepository.findAllWithMembers().stream()
                .map(this::toDto)
                .toList();
    }

    public DormantApprovalListDTO getApprovalList(String listId) {
        assertMayManageLists();
        return toDto(requireList(listId));
    }

    public List<DormantMemberDTO> getMembersByListId(String listId) {
        assertMayManageLists();
        DormantApprovalList entity = requireList(listId);
        return mapMembers(entity.memberList(), entity);
    }

    /**
     * Records the board's outcome for an Inactivation Approval List (SRS MMD17)
     * and applies it, in one transaction.
     *
     * The board decides each member separately, so the caller sends one decision
     * per member and all of them are applied inside the single surrounding
     * transaction. That matters for three reasons:
     *
     * 1. Validation is complete before anything is written. If one rejection is
     *    missing its mandatory reason, nothing at all is applied - the meeting is
     *    re-recorded from a clean state rather than half-processed.
     * 2. A member's status and the list's status can never drift apart. The
     *    previous implementation left per-member updates to the browser, one call
     *    each, so a failed call produced a list marked processed whose members
     *    were still pending.
     * 3. There is now exactly one path to INACTIVE_DORMANT, and it runs after the
     *    board decision rather than beside it. The old decideMember() had no
     *    board-approval guard while inactivateMembers() did, and the UI called
     *    only the former - so every inactivation the product performed skipped
     *    the check the SRS requires. Collapsing both into this method makes that
     *    unrepresentable rather than merely guarded.
     *
     * The list-level decision is derived here, never taken from the client: a
     * list holding both outcomes is recorded as "Mixed" rather than collapsing to
     * whichever verdict happened to appear first.
     */
    public DormantApprovalListDTO processApprovalList(String listId, DormantApprovalListDTO dto) {
        assertMayManageLists();

        DormantApprovalList entity = requireList(listId);

        // A board meeting is recorded once. Re-processing would drive already
        // inactivated members back through the status guards and fail
        // confusingly, so it is refused up front with a message that says why.
        if (entity.getStatus() == DormantApprovalListStatus.PROCESSED) {
            throw conflict(
                    "Inactivation approval list " + listId + " has already been processed on "
                            + entity.getProcessedAt() + " by " + entity.getProcessedBy());
        }

        if (entity.getEntries().isEmpty()) {
            throw badRequest("Inactivation approval list " + listId + " has no members to process");
        }

        Map<String, DormantMemberDecisionDTO> decisions = indexDecisions(dto.getMemberDecisions());
        validateDecisionsCoverList(entity, decisions);

        LocalDateTime now = LocalDateTime.now();
        int approvedCount = 0;
        int rejectedCount = 0;

        // Iterate the list, not the payload: the list is the authority on who the
        // board actually considered, and validateDecisionsCoverList has already
        // proved the two agree.
        for (DormantApprovalListMember entry : entity.getEntries()) {
            DormantMemberDecisionDTO decision = decisions.get(entry.getMemberNo());
            Member member = entry.getMember();
            MemberStatus before = member.getStatus();
            boolean approved = isApprove(decision.getDecision());

            if (approved) {
                member.setStatus(MemberStatus.INACTIVE_DORMANT);
                entry.setDecision("Approve");
                entry.setRejectReason(null);
                approvedCount++;

                // Consumed AFTER_COMMIT, so an unreachable Finance Module or SMS
                // gateway cannot undo a decision the board has already taken.
                eventPublisher.publishEvent(
                        new DormantInactivationApprovedEvent(member.getMemberId(), entity.getListId()));
            } else {
                // Back where they were before the list, not a blanket assumption
                // of SELECTED_FOR_DORMANT.
                member.setStatus(entry.getPreviousStatus() != null
                        ? entry.getPreviousStatus()
                        : MemberStatus.SELECTED_FOR_DORMANT);
                entry.setDecision("Reject");
                entry.setRejectReason(trimToNull(decision.getRejectReason()));
                rejectedCount++;
            }

            entry.setDecidedAt(now);
            memberRepository.save(member);
            memberStatusHistoryService.record(member, before, member.getStatus(),
                    now.toLocalDate(), approved ? "DORMANT_BOARD_APPROVED" : "DORMANT_BOARD_REJECTED",
                    "Board decision on " + entity.getListId());

            auditService.recordStatusChange(
                    AuditService.MODULE_DORMANT,
                    member.getId(),
                    approved ? "BOARD_APPROVED" : "BOARD_REJECTED",
                    before,
                    member.getStatus(),
                    "Board decision on " + entity.getListId()
                            + (entry.getRejectReason() != null ? "; reason: " + entry.getRejectReason() : "")
            );
        }

        entity.setStatus(DormantApprovalListStatus.PROCESSED);
        entity.setProcessedAt(now);
        entity.setProcessedBy(resolveProcessedBy(dto.getProcessedBy()));
        entity.setActualMeetingDate(
                dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : entity.getBoardMeetingDate());
        entity.setDecision(deriveListDecision(approvedCount, rejectedCount));
        entity.setRejectReason(null);
        entity.setBoardRemarks(dto.getBoardRemarks());

        // Stamped only when something was actually inactivated. The old code set
        // it unconditionally, including on a list where every member was pending.
        if (approvedCount > 0) {
            entity.setInactivatedAt(now);
        }

        // Only overwritten when one is supplied, so re-reading the list never
        // clears an attachment.
        if (trimToNull(dto.getApprovedListDocument()) != null) {
            entity.setApprovedListDocument(dto.getApprovedListDocument().trim());
        }

        DormantApprovalList saved = approvalListRepository.save(entity);

        auditService.record(
                AuditService.MODULE_DORMANT_APPROVAL_LIST,
                saved.getId(),
                "PROCESS_LIST",
                "status=" + DormantApprovalListStatus.CREATED,
                "status=" + saved.getStatus() + ", decision=" + saved.getDecision()
                        + ", approved=" + approvedCount + ", rejected=" + rejectedCount,
                "Board decisions recorded for " + saved.getListId() + " by " + saved.getProcessedBy()
                        + (saved.getBoardRemarks() != null ? "; remarks: " + saved.getBoardRemarks() : "")
        );

        DormantApprovalListDTO result = toDto(saved);
        result.setApprovedCount(approvedCount);
        result.setRejectedCount(rejectedCount);
        return result;
    }

    /**
     * MMD15. A processed list is refused outright rather than unwound: its
     * members are already Inactive (Dormant), and the old code silently left them
     * that way with the list that authorised it deleted - inactive members, no
     * list, no audit trail. Reversing a recorded board decision is a bigger
     * decision than a Delete button should carry.
     */
    public String deleteApprovalList(String listId) {
        assertMayDeleteList();

        DormantApprovalList entity = requireList(listId);

        if (entity.getStatus() == DormantApprovalListStatus.PROCESSED) {
            throw conflict(
                    "Inactivation approval list " + listId + " was processed on " + entity.getProcessedAt()
                            + " and can no longer be deleted. Reactivate the members individually instead.");
        }

        for (DormantApprovalListMember entry : entity.getEntries()) {
            Member member = entry.getMember();

            if (member.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL) {
                MemberStatus restored = entry.getPreviousStatus() != null
                        ? entry.getPreviousStatus()
                        : MemberStatus.SELECTED_FOR_DORMANT;

                member.setStatus(restored);
                memberRepository.save(member);
                memberStatusHistoryService.record(member, MemberStatus.SENT_FOR_DORMANT_APPROVAL,
                        restored, null, "DORMANT_APPROVAL_LIST_DELETED",
                        "Approval list " + entity.getListId() + " deleted; member rolled back");

                auditService.recordStatusChange(
                        AuditService.MODULE_DORMANT,
                        member.getId(),
                        "REMOVED_FROM_APPROVAL_LIST",
                        MemberStatus.SENT_FOR_DORMANT_APPROVAL,
                        restored,
                        "Approval list " + entity.getListId() + " deleted; member rolled back"
                );
            }
        }

        // Written before the delete: once the row is gone its id is no longer
        // readable, and an audit trail that loses the deletion is the one gap
        // that matters most on a list the board was about to sit on.
        auditService.record(
                AuditService.MODULE_DORMANT_APPROVAL_LIST,
                entity.getId(),
                "DELETE_LIST",
                "listId=" + entity.getListId()
                        + ", status=" + entity.getStatus()
                        + ", members=" + entity.getEntries().size(),
                null,
                "Inactivation approval list deleted; "
                        + entity.getEntries().size() + " member(s) returned to their prior status"
        );

        approvalListRepository.delete(entity);
        return "Dormant approval list deleted successfully";
    }

    /**
     * Assembles the Finance Module handoff for one inactivated member (SRS 4.2.7).
     *
     * Read at send time from the repository rather than copied through the event,
     * which is why the event carries only identifiers: by the time
     * FinanceDormantListener runs, the transaction has committed and this reads
     * the state Finance should actually be told about.
     *
     * Read-only and called from an after-commit listener, so it deliberately does
     * NOT assert a role: the caller is the framework, not a user.
     */
    @Transactional(readOnly = true)
    public FinanceDormantHandoffDTO buildFinanceHandoff(String memberId, String listId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> notFound("Member not found: " + memberId));

        DormantApprovalList list = approvalListRepository.findByListId(listId)
                .orElseThrow(() -> notFound("Dormant approval list not found: " + listId));

        FinanceDormantHandoffDTO handoff = new FinanceDormantHandoffDTO();
        handoff.setMemberId(member.getMemberId());
        handoff.setMemberName(member.getFullName() != null
                ? member.getFullName()
                : member.getNameWithInitials());
        handoff.setNic(member.getNic());
        handoff.setListId(list.getListId());
        // The date the board actually sat is the authority for the change, so it
        // is preferred over the date the meeting was scheduled for.
        handoff.setBoardMeetingDate(list.getActualMeetingDate() != null
                ? list.getActualMeetingDate()
                : list.getBoardMeetingDate());
        handoff.setInactivatedOn(list.getInactivatedAt() != null
                ? list.getInactivatedAt().toLocalDate()
                : null);
        return handoff;
    }

    // ---------------------------------------------------------------------
    // Board decision validation (MMD17)
    // ---------------------------------------------------------------------

    /**
     * Keys the incoming decisions by member id, rejecting duplicates rather than
     * letting a later entry silently overwrite an earlier one - two conflicting
     * verdicts for the same member is a caller bug, not a preference.
     */
    private Map<String, DormantMemberDecisionDTO> indexDecisions(
            List<DormantMemberDecisionDTO> memberDecisions
    ) {
        if (memberDecisions == null || memberDecisions.isEmpty()) {
            throw badRequest("A board decision is required for every member in the list");
        }

        Map<String, DormantMemberDecisionDTO> indexed = new HashMap<>();

        for (DormantMemberDecisionDTO decision : memberDecisions) {
            String memberId = trimToNull(decision.getMemberId());

            if (memberId == null) {
                throw badRequest("A board decision was supplied without a member id");
            }

            if (!isApprove(decision.getDecision()) && !isReject(decision.getDecision())) {
                throw badRequest("Decision for " + memberId + " must be Approve or Reject");
            }

            // SRS MMD17: the reason is mandatory for every rejected record before
            // the user may proceed with the approval process.
            if (isReject(decision.getDecision()) && trimToNull(decision.getRejectReason()) == null) {
                throw badRequest("A rejection reason is required for " + memberId);
            }

            if (indexed.putIfAbsent(memberId, decision) != null) {
                throw badRequest("Duplicate board decision supplied for " + memberId);
            }
        }

        return indexed;
    }

    /**
     * Every member in the list must have a decision, and every decision must
     * belong to the list. Both directions are checked so a stale browser tab
     * cannot inactivate a member the board never saw.
     */
    private void validateDecisionsCoverList(
            DormantApprovalList entity,
            Map<String, DormantMemberDecisionDTO> decisions
    ) {
        Set<String> listMemberIds = entity.getEntries().stream()
                .map(DormantApprovalListMember::getMemberNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missing = listMemberIds.stream()
                .filter(memberId -> !decisions.containsKey(memberId))
                .toList();

        if (!missing.isEmpty()) {
            throw badRequest("No board decision supplied for: " + String.join(", ", missing));
        }

        List<String> unknown = decisions.keySet().stream()
                .filter(memberId -> !listMemberIds.contains(memberId))
                .sorted()
                .toList();

        if (!unknown.isEmpty()) {
            throw badRequest(
                    "These members are not part of approval list " + entity.getListId()
                            + ": " + String.join(", ", unknown));
        }
    }

    private String deriveListDecision(int approvedCount, int rejectedCount) {
        if (approvedCount > 0 && rejectedCount > 0) {
            return "Mixed";
        }
        return rejectedCount > 0 ? "Reject" : "Approve";
    }

    /**
     * Prefers the authenticated user over anything the client supplied - who
     * processed a board list is an audit fact, not a form field. Falls back to
     * the supplied value only when there is no authentication at all, which keeps
     * direct service-level tests workable.
     */
    private String resolveProcessedBy(String suppliedProcessedBy) {
        User user = currentUser();

        if (user != null) {
            return user.getFullName() != null && !user.getFullName().isBlank()
                    ? user.getFullName()
                    : user.getUsername();
        }

        String supplied = trimToNull(suppliedProcessedBy);
        return supplied != null ? supplied : "Head Office User";
    }

    /**
     * Sequential rather than a millisecond stamp. The old
     * "DAL-" + System.currentTimeMillis() collided under concurrency on a column
     * declared unique, and produced ids nobody could read out at a meeting.
     *
     * Honest caveat: max+1 is not concurrency-safe either. The unique constraint
     * is what protects it; a database sequence would be the real fix if lists
     * were ever created in bulk.
     */
    private String nextListId() {
        Integer max = approvalListRepository.findMaxListSequence();
        return String.format("DAL-%03d", (max == null ? 0 : max) + 1);
    }

    private DormantApprovalList requireList(String listId) {
        return approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> notFound("Dormant approval list not found"));
    }

    // ---------------------------------------------------------------------
    // Authorization helpers
    // ---------------------------------------------------------------------

    /**
     * The authenticated principal is the User entity itself (see JwtFilter), so
     * it is read straight from the security context rather than passed in: who
     * is acting is a fact about the request, not an argument a caller may pick.
     */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private Role currentRole() {
        User user = currentUser();
        return user != null ? user.getRole() : null;
    }

    /**
     * Null role means there is no authentication at all, which happens in
     * service-level tests and in the scheduler. Those callers are let through -
     * the HTTP boundary is the annotation, and refusing here would mean the
     * scheduled MMD10 run could never execute.
     */
    private void assertRoleIn(Set<Role> allowed, String message) {
        Role role = currentRole();
        if (role == null) {
            return;
        }
        if (!allowed.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void assertMayRead() {
        assertRoleIn(READ_ROLES, "You do not have access to dormant membership profiles");
    }

    private void assertMayConfigure() {
        assertRoleIn(CONFIG_WRITE_ROLES, "Changing the dormancy configuration is restricted to Super Admin");
    }

    private void assertMayRunIdentification() {
        assertRoleIn(IDENTIFICATION_ROLES,
                "Running the dormant identification process is restricted to Head Office");
    }

    private void assertMayManageLists() {
        assertRoleIn(BOARD_ROLES,
                "Inactivation Approval Lists are restricted to Head Office and Board Secretariat personnel");
    }

    private void assertMayDeleteList() {
        assertRoleIn(DELETE_ROLES, "Deleting an Inactivation Approval List requires delete privileges");
    }

    // ---------------------------------------------------------------------
    // District scoping (MMD12)
    // ---------------------------------------------------------------------

    /**
     * The district a dormant member is scoped by. Deliberately submissionLocation
     * and not educationalDistrict: Member documents the distinction - the former
     * is the District Office branch that administers the member, the latter is
     * where they work. User.assignedDistrict names an office, and Termination and
     * Death Donation both compare it against submissionLocation. Scoping on the
     * working district would show a district user members another office
     * administers and hide members they do.
     *
     * The field choice lives here alone so it can be changed in one place if the
     * client reads 4.2.3 the other way.
     */
    private String districtOf(Member member) {
        return member.getSubmissionLocation();
    }

    /**
     * Which locations this caller may see. For a DISTRICT_OFFICE user the
     * requested locations are ignored rather than intersected - the dropdown is
     * a convenience, not the boundary. Head Office, Board Secretary and Super
     * Admin may ask for any set, or for all of them.
     *
     * Returns null to mean "no location restriction". Note the difference that
     * makes for members with no submission location recorded: an unrestricted
     * caller still sees them, a district-scoped caller does not, because a null
     * location cannot be proven to belong to their district.
     */
    private Set<String> resolveVisibleLocations(List<String> requestedLocations) {
        User user = currentUser();

        if (user != null && user.getRole() == Role.DISTRICT_OFFICE) {
            String assigned = user.getAssignedDistrict();

            if (assigned == null || assigned.isBlank()) {
                // A district user with no district assigned can be scoped to
                // nothing safely, but not to everything.
                return Set.of();
            }

            return Set.of(assigned);
        }

        if (requestedLocations == null || requestedLocations.isEmpty()) {
            return null;
        }

        Set<String> cleaned = requestedLocations.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(location -> !location.isEmpty() && !"All".equalsIgnoreCase(location))
                .collect(Collectors.toSet());

        return cleaned.isEmpty() ? null : cleaned;
    }

    /** True when the member falls inside the caller's visible locations. */
    private boolean withinVisibleLocations(Member member, Set<String> visible) {
        if (visible == null) {
            return true;
        }
        String district = districtOf(member);
        return district != null && visible.stream().anyMatch(district::equalsIgnoreCase);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<MemberStatus> resolveStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of(MemberStatus.SELECTED_FOR_DORMANT, MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        }

        List<MemberStatus> resolved = new ArrayList<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            try {
                MemberStatus parsed = MemberStatus.valueOf(status.trim().toUpperCase());
                if (DORMANT_STATUSES.contains(parsed)) {
                    resolved.add(parsed);
                }
            } catch (IllegalArgumentException ignored) {
                // skip unknown status values
            }
        }

        if (resolved.isEmpty()) {
            return List.of(MemberStatus.SELECTED_FOR_DORMANT, MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        }
        return resolved;
    }

    private LocalDate[] resolveDateRange(String dateFilter, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        if (dateFilter == null) {
            return new LocalDate[]{null, null};
        }
        switch (dateFilter.trim().toLowerCase()) {
            case "thismonth": {
                LocalDate start = today.withDayOfMonth(1);
                LocalDate end = start.plusMonths(1).minusDays(1);
                return new LocalDate[]{start, end};
            }
            case "thisandlastmonth": {
                LocalDate start = today.withDayOfMonth(1).minusMonths(1);
                LocalDate end = today.withDayOfMonth(1).plusMonths(1).minusDays(1);
                return new LocalDate[]{start, end};
            }
            case "dateperiod":
                return new LocalDate[]{fromDate, toDate};
            case "all":
            default:
                return new LocalDate[]{null, null};
        }
    }

    private boolean matchesDateRange(LocalDate date, LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return end == null || !date.isAfter(end);
    }

    private boolean matchesSearch(Member member, String term) {
        if (term.isEmpty()) {
            return true;
        }
        return contains(member.getFullName(), term)
                || contains(member.getNameWithInitials(), term)
                || contains(member.getMemberId(), term)
                || contains(member.getNic(), term);
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private void sort(List<DormantMemberDTO> members, String sortBy, String sortOrder) {
        Comparator<DormantMemberDTO> comparator;
        String key = sortBy == null ? "lastactivity" : sortBy.trim().toLowerCase();

        switch (key) {
            case "membershipdate":
                comparator = Comparator.comparing(DormantMemberDTO::getMembershipDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "memberid":
                comparator = Comparator.comparing(DormantMemberDTO::getMemberId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "lastactivity":
            default:
                comparator = Comparator.comparing(DormantMemberDTO::getLastActivityDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
        }

        if (!"asc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        members.sort(comparator);
    }

    /**
     * Bulk obligation lookup. mapMember used to call existsByMemberId once per
     * row, which is one query per member on a screen whose whole job is showing
     * hundreds of them; findMemberIdsWithObligations already existed for exactly
     * this and was going unused.
     */
    private Set<String> obligationsFor(Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(obligationRepository.findMemberIdsWithObligations(memberIds));
    }

    /**
     * Maps a batch of members, resolving loan obligations in one query.
     *
     * When a list is supplied, each row also carries the board decision recorded
     * against it and whether the member has transacted since being listed - the
     * latter so the board is not asked to inactivate somebody who paid in last
     * week without at least being told.
     */
    private List<DormantMemberDTO> mapMembers(List<Member> members, DormantApprovalList list) {
        Set<String> withObligations = obligationsFor(
                members.stream().map(Member::getMemberId).filter(Objects::nonNull).toList());

        Map<String, DormantApprovalListMember> entriesByMember = list == null
                ? Map.of()
                : list.getEntries().stream()
                        .filter(e -> e.getMemberNo() != null)
                        .collect(Collectors.toMap(
                                DormantApprovalListMember::getMemberNo, e -> e, (a, b) -> a));

        return members.stream()
                .map(member -> {
                    DormantMemberDTO dto = mapMember(member, withObligations);
                    DormantApprovalListMember entry = entriesByMember.get(member.getMemberId());

                    if (entry != null) {
                        dto.setDecision(entry.getDecision());
                        dto.setRejectReason(entry.getRejectReason());
                    }
                    if (list != null) {
                        dto.setActivitySinceListing(hasActivitySinceListing(member, list));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * True when the member's account was touched after the list was assembled.
     * Computed rather than stored - it is only meaningful relative to one list,
     * and a column would go stale the moment the member transacted again.
     */
    private boolean hasActivitySinceListing(Member member, DormantApprovalList list) {
        LocalDate lastActivity = member.getLastActivityDate();
        return lastActivity != null
                && list.getCreatedAt() != null
                && !lastActivity.isBefore(list.getCreatedAt().toLocalDate());
    }

    private DormantMemberDTO mapMember(Member member, Set<String> withObligations) {
        DormantMemberDTO dto = new DormantMemberDTO();
        dto.setId(member.getId());
        dto.setMemberId(member.getMemberId());
        dto.setFullName(member.getFullName());
        dto.setNameWithInitials(member.getNameWithInitials());
        dto.setNic(member.getNic());
        dto.setMemberType(member.getMemberType());
        dto.setLocation(districtOf(member));
        dto.setEducationalDistrict(member.getEducationalDistrict());
        dto.setLastActivityDate(member.getLastActivityDate());
        dto.setMembershipDate(member.getMembershipStartDate());
        dto.setDormantSelectionDate(member.getDormantSelectionDate());
        dto.setStatus(member.getStatus() != null ? member.getStatus().name() : null);
        dto.setHasIndirectObligations(
                member.getMemberId() != null && withObligations.contains(member.getMemberId()));
        return dto;
    }

    private DormantApprovalListDTO toDto(DormantApprovalList entity) {
        DormantApprovalListDTO dto = new DormantApprovalListDTO();
        dto.setId(entity.getId());
        dto.setListId(entity.getListId());
        dto.setBoardMeetingId(entity.getBoardMeetingId());
        dto.setBoardMeetingDate(entity.getBoardMeetingDate());
        dto.setActualMeetingDate(entity.getActualMeetingDate());
        dto.setMemberIds(entity.getEntries().stream()
                .map(DormantApprovalListMember::getMemberNo)
                .collect(Collectors.toList()));
        dto.setMembers(mapMembers(entity.memberList(), entity));
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setProcessedBy(entity.getProcessedBy());
        dto.setDecision(entity.getDecision());
        dto.setRejectReason(entity.getRejectReason());
        dto.setBoardRemarks(entity.getBoardRemarks());
        dto.setInactivatedAt(entity.getInactivatedAt());
        dto.setApprovedListDocument(entity.getApprovedListDocument());
        return dto;
    }

    private DormantConfigDTO toConfigDto(DormantConfig config) {
        DormantConfigDTO dto = new DormantConfigDTO();
        dto.setDormantPeriodMonths(config.getDormantPeriodMonths());
        dto.setScheduleDayOfMonth(config.getScheduleDayOfMonth());
        dto.setScheduleHour(config.getScheduleHour());
        dto.setScheduleMinute(config.getScheduleMinute());
        dto.setEnabled(config.getEnabled());
        dto.setLastRunOn(config.getLastRunOn());
        dto.setLastRunSelectedCount(config.getLastRunSelectedCount());
        dto.setLastRunClearedCount(config.getLastRunClearedCount());
        return dto;
    }

    private boolean isApprove(String decision) {
        return decision != null && "APPROVE".equalsIgnoreCase(decision.trim());
    }

    private boolean isReject(String decision) {
        return decision != null && "REJECT".equalsIgnoreCase(decision.trim());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Bare RuntimeException surfaced as HTTP 500 for every business rule, which
    // gave the UI no way to tell "you may not do that" from "the server broke".
    // GlobalExceptionHandler already honours the status carried here.

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /**
     * How many lists sit in the given statuses.
     *
     * Answered with a COUNT in the database. The dashboard previously fetched every
     * list and filtered in the browser to arrive at the same number.
     */
    public long countApprovalListsByStatuses(java.util.List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return approvalListRepository.count();
        }
        java.util.List<com.memberconnect.backend.enums.DormantApprovalListStatus> parsed =
                new java.util.ArrayList<>();
        for (String status : statuses) {
            try {
                parsed.add(com.memberconnect.backend.enums.DormantApprovalListStatus
                        .valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // An unrecognised status matches nothing rather than everything.
            }
        }
        return parsed.isEmpty() ? 0L : approvalListRepository.countByStatusIn(parsed);
    }
}
