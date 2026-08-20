package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.CauseOfDeathDTO;
import com.memberconnect.backend.dto.FinanceMemberDeathHandoffDTO;
import com.memberconnect.backend.dto.MemberDeathDocumentDTO;
import com.memberconnect.backend.dto.MemberDeathMinorDisbursementDTO;
import com.memberconnect.backend.dto.MemberDeathRecordDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.enums.MemberDeathRecordStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.config.MemberDeathDocumentSeeder;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.event.MemberDeathApprovedEvent;
import com.memberconnect.backend.event.MemberDeathCompletedEvent;
import com.memberconnect.backend.event.MemberDeathMarkedIncompleteEvent;
import com.memberconnect.backend.event.MemberDeathRejectedEvent;
import com.memberconnect.backend.model.CauseOfDeath;
import com.memberconnect.backend.model.Loan;
import com.memberconnect.backend.model.LoanObligation;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathDocument;
import com.memberconnect.backend.model.MemberDeathMinorAccount;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.CauseOfDeathRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberDeathDocumentRepository;
import com.memberconnect.backend.repository.MemberDeathMinorAccountRepository;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@SuppressWarnings("null")
public class MemberDeathRecordService {

    /**
     * The MMT21 status matrix, transcribed verbatim. A transition absent from this
     * map is not a permitted manual status change - the approval path moves through
     * the dedicated forward/approve/reject actions, not through changeStatus.
     */
    private static final Map<MemberDeathRecordStatus, Set<MemberDeathRecordStatus>> ALLOWED_STATUS_CHANGES = Map.of(
            MemberDeathRecordStatus.NEW, Set.of(MemberDeathRecordStatus.INACTIVE),
            MemberDeathRecordStatus.INCOMPLETE, Set.of(MemberDeathRecordStatus.NEW, MemberDeathRecordStatus.INACTIVE),
            MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL, Set.of(MemberDeathRecordStatus.NEW, MemberDeathRecordStatus.INACTIVE),
            MemberDeathRecordStatus.DISTRICT_COMMITTEE, Set.of(MemberDeathRecordStatus.NEW, MemberDeathRecordStatus.INACTIVE),
            MemberDeathRecordStatus.PD_COMMITTEE, Set.of(MemberDeathRecordStatus.NEW, MemberDeathRecordStatus.INACTIVE),
            MemberDeathRecordStatus.REJECTED, Set.of(MemberDeathRecordStatus.NEW, MemberDeathRecordStatus.INACTIVE),
            MemberDeathRecordStatus.INACTIVE, Set.of(MemberDeathRecordStatus.NEW)
    );

    /** Statuses after which uploaded files may no longer be added or removed (MMT18). */
    private static final Set<MemberDeathRecordStatus> DOCUMENT_LOCK_STATUSES = Set.of(
            MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL,
            MemberDeathRecordStatus.DISTRICT_COMMITTEE,
            MemberDeathRecordStatus.PD_COMMITTEE,
            MemberDeathRecordStatus.APPROVED,
            MemberDeathRecordStatus.REJECTED
    );

    /**
     * The escalation ladder (MMT22 -> MMT23 -> MMT24). Forward-only by construction:
     * there is no entry mapping a committee back to an earlier one.
     */
    private static final Map<MemberDeathRecordStatus, MemberDeathRecordStatus> FORWARD_TRANSITIONS = Map.of(
            MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL, MemberDeathRecordStatus.DISTRICT_COMMITTEE,
            MemberDeathRecordStatus.DISTRICT_COMMITTEE, MemberDeathRecordStatus.PD_COMMITTEE
    );

    /**
     * Which role owns the decision at each level. This is the runtime backstop
     * behind the controller annotations, and the thing that actually stops one
     * clerk walking a record through all three levels alone.
     */
    private static final Map<MemberDeathRecordStatus, Role> DECISION_ROLE = Map.of(
            MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL, Role.DISTRICT_OFFICE,
            MemberDeathRecordStatus.DISTRICT_COMMITTEE, Role.DISTRICT_COMMITTEE,
            MemberDeathRecordStatus.PD_COMMITTEE, Role.PD_COMMITTEE
    );

    /**
     * Who may see a member death record at all (MMT19 / MMT20). The District Office
     * raises them, the three decision levels read them before deciding, and Head
     * Office oversees. Deliberately excludes ACCOUNTS, SCHOLARSHIP_OFFICER and
     * DEATH_DONATION_OFFICER: none of them is an actor in SRS section 4.
     *
     * Kept in step with MemberDeathRecordController.READ_ROLES - the annotations are
     * the outer gate, this set is what the generic document routes consult.
     */
    private static final Set<Role> DEATH_READ_ROLES = Set.of(
            Role.DISTRICT_OFFICE,
            Role.DISTRICT_COMMITTEE,
            Role.PD_COMMITTEE,
            Role.HEAD_OFFICE,
            Role.BOARD_SECRETARY,
            Role.SUPER_ADMIN
    );

    /** Who may raise or change one (MMT18 / MMT21): the District Office alone. */
    private static final Set<Role> DEATH_ENTRY_ROLES = Set.of(
            Role.DISTRICT_OFFICE,
            Role.SUPER_ADMIN
    );

    /**
     * Only consulted by the legacy fallback in validateMandatoryDocuments, for
     * records whose files predate the move to the Supporting Documents master.
     */
    private static final Set<String> MANDATORY_DOCUMENT_TYPES = Set.of("DEATH_CERTIFICATE");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemberDeathRecordRepository recordRepository;
    private final MemberDeathDocumentRepository documentRepository;
    private final MemberDeathMinorAccountRepository minorAccountRepository;
    private final MemberRepository memberRepository;
    private final CauseOfDeathRepository causeOfDeathRepository;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;
    private final LoanRepository loanRepository;
    private final LoanObligationRepository obligationRepository;
    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;
    private final S3Service s3Service;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final DeathDonationEntitlementService entitlementService;
    private final DocumentService documentService;

    public MemberDeathRecordService(
            MemberDeathRecordRepository recordRepository,
            MemberDeathDocumentRepository documentRepository,
            MemberDeathMinorAccountRepository minorAccountRepository,
            MemberRepository memberRepository,
            CauseOfDeathRepository causeOfDeathRepository,
            MinorSavingsAccountRepository minorSavingsAccountRepository,
            LoanRepository loanRepository,
            LoanObligationRepository obligationRepository,
            BankRepository bankRepository,
            BranchRepository branchRepository,
            S3Service s3Service,
            ApplicationEventPublisher eventPublisher,
            AuditService auditService,
            DeathDonationEntitlementService entitlementService,
            DocumentService documentService
    ) {
        this.recordRepository = recordRepository;
        this.documentRepository = documentRepository;
        this.minorAccountRepository = minorAccountRepository;
        this.memberRepository = memberRepository;
        this.causeOfDeathRepository = causeOfDeathRepository;
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
        this.loanRepository = loanRepository;
        this.obligationRepository = obligationRepository;
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
        this.s3Service = s3Service;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.entitlementService = entitlementService;
        this.documentService = documentService;
    }

    public List<CauseOfDeathDTO> getCauseOfDeathOptions() {
        return causeOfDeathRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::mapCauseToDto)
                .toList();
    }

    public MemberRetirementValidationDTO validateMemberForDeathRecord(String memberId) {
        List<Loan> ownLoans = loanRepository.findByMemberId(memberId);
        List<LoanObligation> obligations = obligationRepository.findByMemberId(memberId);

        boolean hasOutstandingLoans = !ownLoans.isEmpty();
        boolean hasLoanObligations = !obligations.isEmpty();
        double totalOutstandingBalance = ownLoans.stream()
                .mapToDouble(Loan::getBalance)
                .sum();

        MemberRetirementValidationDTO dto = new MemberRetirementValidationDTO();
        dto.setHasOutstandingLoans(hasOutstandingLoans);
        dto.setHasLoanObligations(hasLoanObligations);
        dto.setTotalOutstandingLoanBalance(totalOutstandingBalance);
        dto.setCanSubmit(!hasOutstandingLoans && !hasLoanObligations);
        dto.setMessage(buildValidationMessage(hasOutstandingLoans, hasLoanObligations));
        return dto;
    }

    public List<MemberDeathRecordDTO> searchRequests(
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder,
            List<String> locations
    ) {
        Set<String> effectiveLocations = resolveVisibleLocations(locations);

        // Filtering happens on the entities first. Mapping used to run ahead of the
        // filters, which meant a search that matched nothing still paid the full
        // per-record mapping cost for every row in the table.
        List<MemberDeathRecord> matches = recordRepository.findAllWithMember()
                .stream()
                .filter(record -> matchesLocation(record, effectiveLocations))
                .filter(record -> matchesStatuses(record, statuses))
                .filter(record -> matchesInformedDateRange(record, fromDate, toDate))
                .filter(record -> matchesSearch(record, searchKey))
                .sorted((left, right) -> compareForSort(left, right, sortBy, sortOrder))
                .toList();

        return mapToResponses(matches);
    }

    /**
     * Batch counterpart of mapToResponse: resolves loans, obligations, causes of
     * death, bank and branch names, minor accounts and documents for a whole page
     * in a fixed number of queries rather than roughly eight per row. The DTOs it
     * produces are identical to mapping each record individually.
     */
    private List<MemberDeathRecordDTO> mapToResponses(List<MemberDeathRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }

        DeathRecordLookups lookups = loadLookups(records);

        return records.stream()
                .map(record -> buildResponse(record, lookups))
                .toList();
    }

    private DeathRecordLookups loadLookups(List<MemberDeathRecord> records) {
        List<String> memberIds = records.stream()
                .map(MemberDeathRecord::getMember)
                .filter(Objects::nonNull)
                .map(Member::getMemberId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> recordIds = records.stream()
                .map(MemberDeathRecord::getId)
                .filter(Objects::nonNull)
                .toList();
        List<String> recordNos = records.stream()
                .map(MemberDeathRecord::getRecordId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<Long> bankIds = records.stream()
                .map(MemberDeathRecord::getNomineeBankId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> branchIds = records.stream()
                .map(MemberDeathRecord::getNomineeBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Sorting the child rows by id before grouping keeps them in the same
        // order the lazy collections would have produced them in.
        return new DeathRecordLookups(
                memberIds.isEmpty()
                        ? Set.of()
                        : new HashSet<>(loanRepository.findMemberIdsWithPositiveBalance(memberIds)),
                memberIds.isEmpty()
                        ? Set.of()
                        : new HashSet<>(obligationRepository.findMemberIdsWithObligations(memberIds)),
                causeOfDeathRepository.findByActiveTrueOrderByNameAsc(),
                bankIds.isEmpty()
                        ? Map.of()
                        : bankRepository.findAllById(bankIds).stream()
                                .collect(Collectors.toMap(bank -> bank.getId(), bank -> bank.getName(),
                                        (first, second) -> first)),
                branchIds.isEmpty()
                        ? Map.of()
                        : branchRepository.findAllById(branchIds).stream()
                                .collect(Collectors.toMap(branch -> branch.getId(), branch -> branch.getName(),
                                        (first, second) -> first)),
                recordIds.isEmpty()
                        ? Map.of()
                        : minorAccountRepository.findByRecord_IdIn(recordIds).stream()
                                .sorted(Comparator.comparing(MemberDeathMinorAccount::getId))
                                .collect(Collectors.groupingBy(item -> item.getRecord().getId())),
                recordNos.isEmpty()
                        ? Map.of()
                        : documentRepository.findByRecord_RecordIdIn(recordNos).stream()
                                .sorted(Comparator.comparing(MemberDeathDocument::getId))
                                .collect(Collectors.groupingBy(document -> document.getRecord().getRecordId()))
        );
    }

    /** Per-page lookups shared by every row of a list response. */
    private record DeathRecordLookups(
            Set<String> membersWithLoanBalance,
            Set<String> membersWithObligations,
            List<CauseOfDeath> causes,
            Map<Long, String> bankNames,
            Map<Long, String> branchNames,
            Map<Long, List<MemberDeathMinorAccount>> minorAccountsByRecordId,
            Map<String, List<MemberDeathDocument>> documentsByRecordNo
    ) {}

    public List<MemberDeathRecordDTO> getRecordsByMember(String memberId) {
        return recordRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public MemberDeathRecordDTO getRecordByRecordNo(String recordNo) {
        return mapToResponse(getRecordEntity(recordNo));
    }

    public MemberDeathRecordDTO getActiveRecordForMember(String memberId) {
        return recordRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .filter(record -> record.getStatus() != MemberDeathRecordStatus.INACTIVE)
                .findFirst()
                .map(this::mapToResponse)
                .orElse(null);
    }

    public MemberDeathRecordDTO saveRecord(String memberId, MemberDeathRecordDTO dto) {
        Member member = getMemberForSave(memberId);
        assertCallerMayActFor(member);
        MemberDeathRecord record;

        if (dto.getRecordNo() != null && !dto.getRecordNo().isBlank()) {
            record = getRecordEntity(dto.getRecordNo());
            assertCallerMayAccess(record);
            if (!record.getMember().getMemberId().equals(memberId)) {
                throw new RuntimeException("Record does not belong to the specified member");
            }
            if (!isEditable(record.getStatus())) {
                throw new RuntimeException("Record cannot be edited in its current status");
            }
        } else {
            MemberDeathRecord existing = recordRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId)
                    .stream()
                    .filter(r -> r.getStatus() != MemberDeathRecordStatus.INACTIVE)
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                record = existing;
                if (!isEditable(record.getStatus())) {
                    throw new RuntimeException("Record cannot be edited in its current status");
                }
            } else {
                record = new MemberDeathRecord();
                record.setRecordId(generateRecordId());
                record.setMember(member);
                record.setStatus(MemberDeathRecordStatus.NEW);
                // Stamped once, at creation. Re-deriving it later would move the
                // record between districts whenever the member transfers.
                record.setSubmissionLocation(resolveSubmissionLocationFor(member));
                record.setCreatedBy(currentUsername());
            }
        }

        validateRecordDto(dto, false);
        applyRecordFields(record, member, dto);
        replaceMinorAccounts(record, dto.getMinorDisbursements());

        // MMT18: the donation figures appear once the record has been saved and
        // retrieved back, so they are derived here rather than on the entry form.
        entitlementService.populateFromFinance(record);
        MemberDeathRecord saved = recordRepository.save(record);

        if (member.getStatus() == MemberStatus.ACTIVE) {
            member.setStatus(MemberStatus.MEMBER_DEATH_RECORDED);
            memberRepository.save(member);
        }

        return mapToResponse(saved);
    }

    /**
     * Applies the operator overrides behind the SRS refresh button and recalculates
     * the rest of the entitlement.
     *
     * Available while the record is still with an approval level, not just while it
     * is editable: the SRS lets an authorised user adjust these amounts in View
     * Mode, which is the only way a committee can correct a figure it disagrees
     * with without sending the whole record back.
     */
    public MemberDeathRecordDTO refreshDonationEntitlement(
            String recordNo,
            Integer monthsRemitted,
            BigDecimal receivedPast12Months,
            BigDecimal creditedToSpecialFixedAccount
    ) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        assertCallerMayAccess(record);

        if (record.getStatus() == MemberDeathRecordStatus.APPROVED
                || record.getStatus() == MemberDeathRecordStatus.REJECTED
                || record.getStatus() == MemberDeathRecordStatus.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Death donation amounts cannot be changed once the record is " + record.getStatus());
        }

        entitlementService.applyOverrides(
                record, monthsRemitted, receivedPast12Months, creditedToSpecialFixedAccount);

        MemberDeathRecord saved = recordRepository.save(record);

        auditService.record(AuditService.MODULE_MEMBER_DEATH, saved.getId(),
                "DONATION_RECALCULATED", null,
                String.valueOf(saved.getDisburseDonationAmount()),
                "Death donation recalculated for member " + saved.getMember().getMemberId());

        return mapToResponse(saved);
    }

    public MemberDeathRecordDTO submitRecord(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        assertCallerMayAccess(record);

        if (!isSubmittable(record.getStatus())) {
            throw new RuntimeException("Record cannot be submitted in its current status");
        }

        MemberRetirementValidationDTO validation = validateMemberForDeathRecord(record.getMember().getMemberId());
        if (!validation.isCanSubmit()) {
            throw new RuntimeException(validation.getMessage());
        }

        validateRecordDto(mapToResponse(record), true);
        validateMandatoryDocuments(record);
        validateMinorAccountsForSubmit(record);

        record.setStatus(MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL);
        return mapToResponse(recordRepository.save(record));
    }

    public MemberDeathRecordDTO markIncomplete(String recordNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Incomplete reason is required");
        }

        MemberDeathRecord record = getRecordEntity(recordNo);
        if (!isSubmittable(record.getStatus()) && record.getStatus() != MemberDeathRecordStatus.INCOMPLETE) {
            throw new RuntimeException("Record cannot be marked incomplete in its current status");
        }

        assertCallerMayAccess(record);

        MemberDeathRecordStatus previousStatus = record.getStatus();
        record.setStatus(MemberDeathRecordStatus.INCOMPLETE);
        record.setIncompleteReason(reason.trim());
        MemberDeathRecord saved = recordRepository.save(record);

        auditService.recordStatusChange(AuditService.MODULE_MEMBER_DEATH, saved.getId(),
                "MARK_INCOMPLETE", previousStatus, MemberDeathRecordStatus.INCOMPLETE,
                saved.getIncompleteReason());

        eventPublisher.publishEvent(new MemberDeathMarkedIncompleteEvent(
                saved.getMember().getMemberId(), saved.getRecordId(), saved.getIncompleteReason()));

        return mapToResponse(saved);
    }

    /**
     * Manual status change within the MMT21 matrix. The approval ladder itself is
     * NOT reachable from here - forwarding and deciding go through their own
     * actions, which carry the per-level role checks.
     */
    public MemberDeathRecordDTO changeStatus(String recordNo, String statusValue) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        assertCallerMayAccess(record);

        MemberDeathRecordStatus currentStatus = record.getStatus();
        MemberDeathRecordStatus newStatus = parseStatus(statusValue);

        if (currentStatus == newStatus) {
            return mapToResponse(record);
        }

        Set<MemberDeathRecordStatus> permitted =
                ALLOWED_STATUS_CHANGES.getOrDefault(currentStatus, Set.of());
        if (!permitted.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Status cannot be changed from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == MemberDeathRecordStatus.INACTIVE && !currentUserHasInactiveRights()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have rights to make a member death record inactive");
        }

        if (newStatus == MemberDeathRecordStatus.NEW) {
            record.setIncompleteReason(null);
            record.setRejectReason(null);
        }

        record.setStatus(newStatus);
        MemberDeathRecord saved = recordRepository.save(record);

        // Keep the member profile in step. MMT21: making the record inactive puts
        // the member back to Active; reopening it puts them back under a recorded
        // death, so the profile must not be left claiming they are still active.
        Member member = record.getMember();
        MemberStatus previousMemberStatus = member.getStatus();
        if (newStatus == MemberDeathRecordStatus.INACTIVE) {
            member.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(member);
        } else if (newStatus == MemberDeathRecordStatus.NEW
                && member.getStatus() == MemberStatus.ACTIVE) {
            member.setStatus(MemberStatus.MEMBER_DEATH_RECORDED);
            memberRepository.save(member);
        }

        auditService.recordStatusChange(AuditService.MODULE_MEMBER_DEATH, saved.getId(),
                "STATUS_CHANGE", currentStatus, newStatus,
                "Manual status change for member " + member.getMemberId());
        auditMemberStatusChange(saved, previousMemberStatus, member.getStatus(), "Record status change");

        return mapToResponse(saved);
    }

    /**
     * Approve at whichever level the record currently sits (MMT22 / MMT23 / MMT24).
     *
     * The member moves to MEMBER_DEATH_APPROVED, NOT to DECEASED: the SRS stops
     * remittance here and leaves the final status to the Finance Module once every
     * savings account is closed (MMT25, completeMemberDeath below).
     */
    public MemberDeathRecordDTO approveRecord(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        MemberDeathRecordStatus decisionLevel = assertDecidableLevel(record, "approved");
        assertCallerMayAccess(record);
        assertMayDecideAtCurrentLevel(record);
        assertNotSelfApproval(record);

        record.setStatus(MemberDeathRecordStatus.APPROVED);
        record.setRejectReason(null);
        stampDecision(record, decisionLevel);
        MemberDeathRecord saved = recordRepository.save(record);

        Member member = record.getMember();
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.MEMBER_DEATH_APPROVED);
        memberRepository.save(member);

        auditService.recordStatusChange(AuditService.MODULE_MEMBER_DEATH, saved.getId(),
                "APPROVE", decisionLevel, MemberDeathRecordStatus.APPROVED,
                "Approved at " + levelName(decisionLevel) + " for member " + member.getMemberId());
        auditMemberStatusChange(saved, previousMemberStatus, member.getStatus(),
                "Member death approved at " + levelName(decisionLevel));

        eventPublisher.publishEvent(
                new MemberDeathApprovedEvent(member.getMemberId(), saved.getRecordId()));

        return mapToResponse(saved);
    }

    /**
     * Reject at whichever level the record currently sits. The SRS is explicit at
     * all three levels: the status of the Member Profile is changed back to Active.
     */
    public MemberDeathRecordDTO rejectRecord(String recordNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Reject reason is required");
        }

        MemberDeathRecord record = getRecordEntity(recordNo);
        MemberDeathRecordStatus decisionLevel = assertDecidableLevel(record, "rejected");
        assertCallerMayAccess(record);
        assertMayDecideAtCurrentLevel(record);
        assertNotSelfApproval(record);

        record.setStatus(MemberDeathRecordStatus.REJECTED);
        record.setRejectReason(reason.trim());
        stampDecision(record, decisionLevel);
        MemberDeathRecord saved = recordRepository.save(record);

        Member member = record.getMember();
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);

        auditService.recordStatusChange(AuditService.MODULE_MEMBER_DEATH, saved.getId(),
                "REJECT", decisionLevel, MemberDeathRecordStatus.REJECTED,
                "Rejected at " + levelName(decisionLevel) + ": " + saved.getRejectReason());
        auditMemberStatusChange(saved, previousMemberStatus, member.getStatus(),
                "Member death rejected at " + levelName(decisionLevel));

        eventPublisher.publishEvent(new MemberDeathRejectedEvent(
                member.getMemberId(), saved.getRecordId(), saved.getRejectReason(),
                levelName(decisionLevel)));

        return mapToResponse(saved);
    }

    /**
     * Escalate to the next approval level (MMT22 -> MMT23 -> MMT24). Strictly
     * forward-only: FORWARD_TRANSITIONS has no reverse entries, so a record sitting
     * with the P&D Committee cannot be pushed back down to the District Committee.
     */
    public MemberDeathRecordDTO forwardToNextLevel(String recordNo, String concerns) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        MemberDeathRecordStatus current = record.getStatus();
        MemberDeathRecordStatus next = FORWARD_TRANSITIONS.get(current);

        if (next == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A record in status " + current + " cannot be forwarded to another committee");
        }

        assertCallerMayAccess(record);
        assertMayDecideAtCurrentLevel(record);
        if (current == MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL) {
            assertNotSelfApproval(record);
        }

        appendConcern(record, concerns);
        record.setStatus(next);
        stampDecision(record, current);
        MemberDeathRecord saved = recordRepository.save(record);

        auditService.recordStatusChange(AuditService.MODULE_MEMBER_DEATH, saved.getId(),
                "FORWARD", current, next,
                "Forwarded to " + levelName(next) + " for member " + saved.getMember().getMemberId());

        return mapToResponse(saved);
    }

    /**
     * Finance reports that a deceased member accounts are closed (MMT25). Moves the
     * member from MEMBER_DEATH_APPROVED to DECEASED and notifies the nominee.
     *
     * Safe to retry: a repeat call for a member who is already DECEASED returns the
     * current state without notifying a second time.
     */
    public MemberDeathRecordDTO completeMemberDeath(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);

        if (record.getStatus() != MemberDeathRecordStatus.APPROVED) {
            throw new RuntimeException("Member death record " + recordNo
                    + " cannot be completed from status " + record.getStatus()
                    + ". Only approved records can be completed.");
        }

        Member member = record.getMember();

        if (member.getStatus() == MemberStatus.DECEASED) {
            // Already done on an earlier call. Returning quietly keeps Finance
            // retries harmless; re-notifying would tell the nominee twice.
            return mapToResponse(record);
        }

        if (member.getStatus() != MemberStatus.MEMBER_DEATH_APPROVED) {
            throw new RuntimeException("Member " + member.getMemberId()
                    + " cannot be marked deceased from status " + member.getStatus()
                    + ". Expected MEMBER_DEATH_APPROVED.");
        }

        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.DECEASED);
        memberRepository.save(member);

        auditService.record(AuditService.MODULE_MEMBER_DEATH, record.getId(),
                "FINANCE_COMPLETION", null, "COMPLETED",
                "Finance Module confirmed all accounts closed for record " + record.getRecordId());
        auditMemberStatusChange(record, previousMemberStatus, member.getStatus(), "Finance completion");

        eventPublisher.publishEvent(
                new MemberDeathCompletedEvent(member.getMemberId(), record.getRecordId()));

        return mapToResponse(record);
    }

    /**
     * Assembles the MMT25 payload for the Finance Module.
     *
     * Read-only and separate from approveRecord so the entity graph is walked
     * inside a transaction on the listener thread, rather than the client holding
     * a detached record. Mirrors TerminationService.buildFinanceHandoff.
     */
    @Transactional(readOnly = true)
    public FinanceMemberDeathHandoffDTO buildFinanceHandoff(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        Member member = record.getMember();

        FinanceMemberDeathHandoffDTO handoff = new FinanceMemberDeathHandoffDTO();
        handoff.setRecordNo(record.getRecordId());
        handoff.setMemberId(member.getMemberId());
        handoff.setMemberName(firstNonBlank(member.getNameWithInitials(), member.getFullName()));
        handoff.setNic(member.getNic());
        handoff.setDeceasedDate(record.getDeceasedDate());
        handoff.setCauseOfDeath(record.getCauseOfDeath());

        handoff.setNomineeFullName(record.getNomineeFullName());
        handoff.setNomineeBank(record.getBank());
        handoff.setNomineeBranch(record.getBankBranch());
        handoff.setNomineeAccountNo(firstNonBlank(record.getNomineeAccountNo(), record.getAccountNumber()));

        handoff.setDisburseDonationAmount(record.getDisburseDonationAmount());
        handoff.setCreditedToSpecialFixedAccount(record.getCreditedToSpecialFixedAccount());
        handoff.setFuneralAccountNo(record.getFuneralAccountNo());

        handoff.setMinorDisbursements(
                minorAccountRepository.findByRecord_Id(record.getId())
                        .stream()
                        .map(this::mapMinorAccountToDto)
                        .collect(Collectors.toList())
        );

        return handoff;
    }

    public List<MemberDeathDocumentDTO> getDocuments(String recordNo) {
        MemberDeathRecord record = getRecordEntity(recordNo);
        return documentRepository.findByRecord_RecordId(record.getRecordId())
                .stream()
                .map(this::mapDocumentToDto)
                .toList();
    }

    public void deleteDocument(Long documentId) {
        MemberDeathDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!isEditable(document.getRecord().getStatus())) {
            throw new RuntimeException("Documents cannot be deleted after submission");
        }

        deleteDocumentFileQuietly(document);
        documentRepository.delete(document);
    }

    public void seedCauseOfDeathIfEmpty() {
        if (causeOfDeathRepository.count() > 0) {
            return;
        }

        causeOfDeathRepository.saveAll(List.of(
                createCause("NATURAL", "Natural Causes"),
                createCause("ACCIDENT", "Accident"),
                createCause("ILLNESS", "Illness"),
                createCause("OTHER", "Other")
        ));
    }

    private CauseOfDeath createCause(String code, String name) {
        CauseOfDeath cause = new CauseOfDeath();
        cause.setCode(code);
        cause.setName(name);
        cause.setActive(true);
        return cause;
    }

    private Member getMemberForSave(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member.getStatus() != MemberStatus.ACTIVE
                && member.getStatus() != MemberStatus.MEMBER_DEATH_RECORDED) {
            throw new RuntimeException("Only active members can have a death record initiated");
        }

        return member;
    }

    private void validateRecordDto(MemberDeathRecordDTO dto, boolean forSubmit) {
        if (dto.getInformedDate() == null || dto.getInformedDate().isBlank()) {
            throw new RuntimeException("Informed date is required");
        }
        if (dto.getDeceasedDate() == null || dto.getDeceasedDate().isBlank()) {
            throw new RuntimeException("Deceased date is required");
        }
        if (dto.getCauseOfDeathId() == null) {
            throw new RuntimeException("Cause of death is required");
        }

        LocalDate informedDate = LocalDate.parse(dto.getInformedDate());
        LocalDate deceasedDate = LocalDate.parse(dto.getDeceasedDate());
        LocalDate today = LocalDate.now();

        if (informedDate.isAfter(today)) {
            throw new RuntimeException("Informed date cannot be a future date");
        }
        if (deceasedDate.isAfter(today)) {
            throw new RuntimeException("Deceased date cannot be a future date");
        }
        if (deceasedDate.isAfter(informedDate)) {
            throw new RuntimeException("Deceased date cannot be after informed date");
        }

        causeOfDeathRepository.findById(dto.getCauseOfDeathId())
                .orElseThrow(() -> new RuntimeException("Invalid cause of death"));

        if (forSubmit) {
            if (dto.getNomineeMobile() == null || dto.getNomineeMobile().isBlank()) {
                throw new RuntimeException("Nominee mobile number is required");
            }
            if (dto.getNomineeBankId() == null) {
                throw new RuntimeException("Nominee bank is required");
            }
            if (dto.getNomineeBranchId() == null) {
                throw new RuntimeException("Nominee bank branch is required");
            }
            if (dto.getNomineeAccountNo() == null || dto.getNomineeAccountNo().isBlank()) {
                throw new RuntimeException("Nominee account number is required");
            }
        }
    }

    /**
     * MMT18 gates submission on the mandatory supporting documents, and the SRS
     * takes that list from the Supporting Documents for Applications Master - it
     * grows, for instance, when the member has minor savings accounts to close.
     * So the master is the authority here, not a hardcoded set.
     *
     * The legacy per-record check is still applied as a fallback for records whose
     * files were uploaded through the old bespoke path and never backfilled, so an
     * older record cannot slip through with nothing attached.
     */
    private void validateMandatoryDocuments(MemberDeathRecord record) {
        boolean masterSatisfied = documentService.allMandatoryDocumentsUploaded(
                record.getRecordId(),
                record.getMember().getMemberId(),
                MemberDeathDocumentSeeder.MEMBER_DEATH);

        if (masterSatisfied) {
            return;
        }

        if (hasLegacyMandatoryDocuments(record.getRecordId())) {
            return;
        }

        throw new RuntimeException("Cannot submit. Mandatory documents are missing.");
    }

    private boolean hasLegacyMandatoryDocuments(String recordId) {
        List<MemberDeathDocument> documents = documentRepository.findByRecord_RecordId(recordId);
        for (String mandatoryType : MANDATORY_DOCUMENT_TYPES) {
            boolean uploaded = documents.stream()
                    .anyMatch(doc -> mandatoryType.equalsIgnoreCase(doc.getDocumentType()));
            if (!uploaded) {
                return false;
            }
        }
        return true;
    }

    /**
     * Authority for the generic document routes, which carry no role annotations
     * of their own. Mirrors TerminationService.assertDocumentsEditable.
     *
     * Three separate questions, all of which must pass: is the caller a role that
     * may work a death record at all (MMT18 names the District Office System User
     * as the sole actor), is the record in their district, and is the record still
     * in a status where files may change.
     */
    public void assertDocumentsEditable(String recordNo) {
        assertMayEnterDeathRecords();

        MemberDeathRecord record = getRecordEntity(recordNo);
        assertCallerMayAccess(record);

        if (DOCUMENT_LOCK_STATUSES.contains(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Documents cannot be changed once the record is " + record.getStatus());
        }
    }

    /**
     * Read authority for the same generic document routes. Everyone who takes part
     * in the death workflow may read the supporting files - the District Office
     * that uploads them and each approval level that has to look at them before
     * deciding (MMT20 / MMT22-MMT24) - and nobody else.
     */
    public void assertDocumentsReadable(String recordNo) {
        assertMayReadDeathRecords();
        assertCallerMayAccess(getRecordEntity(recordNo));
    }

    /**
     * Role-only variant, for the routes that have no record to scope against yet
     * (the required-documents preview shown before a record is saved).
     */
    public void assertMayReadDeathRecords() {
        Role role = currentRole();
        if (role == null || !DEATH_READ_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your role cannot access member death records");
        }
    }

    /** MMT18/MMT21: raising and editing a record belongs to the District Office. */
    public void assertMayEnterDeathRecords() {
        Role role = currentRole();
        if (role == null || !DEATH_ENTRY_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the District Office can change a member death record");
        }
    }

    private void validateMinorAccountsForSubmit(MemberDeathRecord record) {
        List<MinorSavingsAccount> minorAccounts = minorSavingsAccountRepository
                .findByMemberId(record.getMember().getMemberId());
        if (minorAccounts.isEmpty()) {
            return;
        }

        for (MinorSavingsAccount account : minorAccounts) {
            MemberDeathMinorAccount minorAccount = record.getMinorAccounts().stream()
                    .filter(item -> account.getMinorAccountNo().equals(item.getMinorAccountNumber()))
                    .findFirst()
                    .orElse(null);

            if (minorAccount == null
                    || minorAccount.getDisbursementBank() == null
                    || minorAccount.getDisbursementBank().isBlank()
                    || minorAccount.getBranch() == null
                    || minorAccount.getBranch().isBlank()
                    || minorAccount.getDisbursementAccountNumber() == null
                    || minorAccount.getDisbursementAccountNumber().isBlank()) {
                throw new RuntimeException(
                        "Disbursement details are required for minor savings account: " + account.getMinorAccountNo()
                );
            }
        }
    }

    private void applyRecordFields(MemberDeathRecord record, Member member, MemberDeathRecordDTO dto) {
        CauseOfDeath cause = causeOfDeathRepository.findById(dto.getCauseOfDeathId())
                .orElseThrow(() -> new RuntimeException("Invalid cause of death"));

        String bankName = resolveBankName(dto.getNomineeBankId());
        String branchName = resolveBranchName(dto.getNomineeBranchId());
        String accountNo = defaultString(dto.getNomineeAccountNo());
        String mobile = defaultString(dto.getNomineeMobile());

        record.setInformedDate(LocalDate.parse(dto.getInformedDate()));
        record.setDeceasedDate(LocalDate.parse(dto.getDeceasedDate()));
        record.setCauseOfDeath(cause.getName());
        record.setComment(trimToNull(dto.getComment()));
        record.setConcernsIdentified(trimToNull(dto.getConcernsIdentified()));
        record.setNomineeFullName(member.getNomineeFullName());
        record.setNomineeRelationship(member.getNomineeRelationship());
        record.setNomineeAddress(member.getNomineeAddress());
        record.setNomineeIdentificationTypeAndNumber(buildIdentification(member));
        record.setNomineeMobileNo(mobile);
        record.setNomineeEmailAddress(trimToNull(dto.getNomineeEmail()));
        record.setBank(bankName);
        record.setBankBranch(branchName);
        record.setAccountNumber(accountNo);
        record.setNomineeMobile(mobile);
        record.setNomineeEmail(trimToNull(dto.getNomineeEmail()));
        record.setNomineeBankId(dto.getNomineeBankId());
        record.setNomineeBranchId(dto.getNomineeBranchId());
        record.setNomineeAccountNo(trimToNull(dto.getNomineeAccountNo()));

        if (dto.getDeathDonationAmount() != null) {
            record.setDeathDonationAmount(dto.getDeathDonationAmount());
        }

        if (record.getStatus() == MemberDeathRecordStatus.INCOMPLETE) {
            record.setStatus(MemberDeathRecordStatus.NEW);
            record.setIncompleteReason(null);
        }
    }

    private void replaceMinorAccounts(MemberDeathRecord record, List<MemberDeathMinorDisbursementDTO> items) {
        record.getMinorAccounts().clear();

        if (items == null) {
            return;
        }

        for (MemberDeathMinorDisbursementDTO item : items) {
            if (item.getMinorAccountNo() == null || item.getMinorAccountNo().isBlank()) {
                continue;
            }

            MemberDeathMinorAccount minorAccount = new MemberDeathMinorAccount();
            minorAccount.setRecord(record);
            minorAccount.setMinorAccountNumber(item.getMinorAccountNo().trim());
            minorAccount.setMinorAccountHolderName(trimToNull(item.getHolderName()));
            minorAccount.setDisbursementBank(
                    item.getDisbursementBankName() != null
                            ? item.getDisbursementBankName()
                            : resolveBankName(item.getDisbursementBankId())
            );
            minorAccount.setBranch(
                    item.getDisbursementBranchName() != null
                            ? item.getDisbursementBranchName()
                            : resolveBranchName(item.getDisbursementBranchId())
            );
            minorAccount.setDisbursementAccountNumber(trimToNull(item.getDisbursementAccountNo()));
            record.getMinorAccounts().add(minorAccount);
        }
    }

    /**
     * Single-record mapping, used by the save/submit/fetch endpoints. It keeps
     * reading the in-memory minorAccounts collection rather than re-querying,
     * because the write flows call this straight after mutating that collection.
     * List responses go through mapToResponses, which batches the same lookups
     * across the whole page.
     */
    private MemberDeathRecordDTO mapToResponse(MemberDeathRecord record) {
        Member member = record.getMember();
        String memberId = member != null ? member.getMemberId() : null;
        Long bankId = record.getNomineeBankId();
        Long branchId = record.getNomineeBranchId();

        DeathRecordLookups lookups = new DeathRecordLookups(
                memberId != null && loanRepository.existsByMemberIdAndBalanceGreaterThan(memberId, 0.0)
                        ? Set.of(memberId)
                        : Set.of(),
                memberId != null && obligationRepository.existsByMemberId(memberId)
                        ? Set.of(memberId)
                        : Set.of(),
                causeOfDeathRepository.findByActiveTrueOrderByNameAsc(),
                bankId != null ? Map.of(bankId, resolveBankName(bankId)) : Map.of(),
                branchId != null ? Map.of(branchId, resolveBranchName(branchId)) : Map.of(),
                record.getId() != null ? Map.of(record.getId(), record.getMinorAccounts()) : Map.of(),
                record.getRecordId() != null
                        ? Map.of(record.getRecordId(), documentRepository.findByRecord_RecordId(record.getRecordId()))
                        : Map.of()
        );

        return buildResponse(record, lookups);
    }

    private MemberDeathRecordDTO buildResponse(MemberDeathRecord record, DeathRecordLookups lookups) {
        Member member = record.getMember();

        MemberDeathRecordDTO dto = new MemberDeathRecordDTO();
        dto.setId(record.getId());
        dto.setRecordNo(record.getRecordId());
        dto.setMemberId(member != null ? member.getMemberId() : null);
        dto.setStatus(record.getStatus().name());

        if (member != null) {
            dto.setMemberFullName(member.getFullName());
            dto.setMemberNameWithInitials(member.getNameWithInitials());
            dto.setMemberNic(member.getNic());
        }

        dto.setNomineeFullName(firstNonBlank(record.getNomineeFullName(), member != null ? member.getNomineeFullName() : null));
        dto.setNomineeRelationship(firstNonBlank(record.getNomineeRelationship(), member != null ? member.getNomineeRelationship() : null));
        dto.setNomineeAddress(firstNonBlank(record.getNomineeAddress(), member != null ? member.getNomineeAddress() : null));
        dto.setNomineeIdentificationType(member != null && member.getIdentification() != null
                ? member.getIdentification().name()
                : null);
        dto.setNomineeIdentificationNumber(member != null ? member.getIdentificationNumber() : null);

        dto.setInformedDate(record.getInformedDate().toString());
        dto.setDeceasedDate(record.getDeceasedDate().toString());
        dto.setCauseOfDeathName(record.getCauseOfDeath());
        dto.setCauseOfDeathId(resolveCauseOfDeathId(record.getCauseOfDeath(), lookups.causes()));
        dto.setComment(record.getComment());
        dto.setConcernsIdentified(record.getConcernsIdentified());
        dto.setNomineeMobile(firstNonBlank(record.getNomineeMobile(), record.getNomineeMobileNo()));
        dto.setNomineeEmail(firstNonBlank(record.getNomineeEmail(), record.getNomineeEmailAddress()));
        dto.setNomineeBankId(record.getNomineeBankId());
        dto.setNomineeBranchId(record.getNomineeBranchId());
        dto.setNomineeAccountNo(firstNonBlank(record.getNomineeAccountNo(), record.getAccountNumber()));
        dto.setDeathDonationAmount(record.getDeathDonationAmount());
        dto.setIncompleteReason(record.getIncompleteReason());
        dto.setRejectReason(record.getRejectReason());
        dto.setEditable(isEditable(record.getStatus()));
        dto.setSubmittable(isSubmittable(record.getStatus()));

        dto.setMonthsRemitted(record.getMonthsRemitted());
        dto.setMonthsRemittedEdited(Boolean.TRUE.equals(record.getMonthsRemittedEdited()));
        dto.setMaximumDonationAmount(record.getMaximumDonationAmount());
        dto.setEligibleDonationAmount(record.getEligibleDonationAmount());
        dto.setReceivedPast12Months(record.getReceivedPast12Months());
        dto.setReceivedPast12MonthsEdited(Boolean.TRUE.equals(record.getReceivedPast12MonthsEdited()));
        dto.setFuneralAccountNo(record.getFuneralAccountNo());
        dto.setFuneralAccountCredited(record.getFuneralAccountCredited());
        dto.setFuneralAccountMaximum(record.getFuneralAccountMaximum());
        dto.setCreditedToSpecialFixedAccount(record.getCreditedToSpecialFixedAccount());
        dto.setCreditedToSpecialFixedEdited(Boolean.TRUE.equals(record.getCreditedToSpecialFixedEdited()));
        dto.setDisburseDonationAmount(record.getDisburseDonationAmount());
        dto.setDonationMultiplierApplied(record.getDonationMultiplierApplied());
        dto.setEligiblePeriodWarning(entitlementService.buildEligiblePeriodWarning(record));

        dto.setSubmissionLocation(record.getSubmissionLocation());
        dto.setCreatedBy(record.getCreatedBy());
        dto.setLevel1DecidedBy(record.getLevel1DecidedBy());
        dto.setLevel1DecidedAt(formatTimestamp(record.getLevel1DecidedAt()));
        dto.setLevel2DecidedBy(record.getLevel2DecidedBy());
        dto.setLevel2DecidedAt(formatTimestamp(record.getLevel2DecidedAt()));
        dto.setLevel3DecidedBy(record.getLevel3DecidedBy());
        dto.setLevel3DecidedAt(formatTimestamp(record.getLevel3DecidedAt()));

        dto.setNomineeBankName(firstNonBlank(
                record.getBank(),
                resolveName(record.getNomineeBankId(), lookups.bankNames())));
        dto.setNomineeBranchName(firstNonBlank(
                record.getBankBranch(),
                resolveName(record.getNomineeBranchId(), lookups.branchNames())));

        String memberId = member != null ? member.getMemberId() : null;
        if (memberId != null) {
            dto.setHasLoanBalance(lookups.membersWithLoanBalance().contains(memberId));
            dto.setHasIndirectObligations(lookups.membersWithObligations().contains(memberId));
        }

        dto.setMinorDisbursements(
                lookups.minorAccountsByRecordId().getOrDefault(record.getId(), List.of())
                        .stream()
                        .map(this::mapMinorAccountToDto)
                        .toList());
        dto.setDocuments(
                lookups.documentsByRecordNo().getOrDefault(record.getRecordId(), List.of())
                        .stream()
                        .map(this::mapDocumentToDto)
                        .toList());

        return dto;
    }

    private MemberDeathMinorDisbursementDTO mapMinorAccountToDto(MemberDeathMinorAccount item) {
        MemberDeathMinorDisbursementDTO dto = new MemberDeathMinorDisbursementDTO();
        dto.setId(item.getId());
        dto.setMinorAccountNo(item.getMinorAccountNumber());
        dto.setHolderName(item.getMinorAccountHolderName());
        dto.setDisbursementBankName(item.getDisbursementBank());
        dto.setDisbursementBranchName(item.getBranch());
        dto.setDisbursementAccountNo(item.getDisbursementAccountNumber());
        return dto;
    }

    private MemberDeathDocumentDTO mapDocumentToDto(MemberDeathDocument document) {
        MemberDeathDocumentDTO dto = new MemberDeathDocumentDTO();
        dto.setId(document.getId());
        dto.setRecordNo(document.getRecord().getRecordId());
        dto.setDocumentType(document.getDocumentType());
        dto.setFileName(document.getFileName());
        dto.setFileType(document.getFileType());
        dto.setMandatory(document.isMandatory());
        dto.setUploadedAt(document.getUploadedAt().format(DATE_TIME_FORMATTER));
        return dto;
    }

    private CauseOfDeathDTO mapCauseToDto(CauseOfDeath cause) {
        CauseOfDeathDTO dto = new CauseOfDeathDTO();
        dto.setId(cause.getId());
        dto.setCode(cause.getCode());
        dto.setName(cause.getName());
        return dto;
    }

    private MemberDeathRecord getRecordEntity(String recordNo) {
        return recordRepository.findByRecordId(recordNo)
                .orElseThrow(() -> new RuntimeException("Member death record not found"));
    }

    private boolean isEditable(MemberDeathRecordStatus status) {
        return status == MemberDeathRecordStatus.NEW || status == MemberDeathRecordStatus.INCOMPLETE;
    }

    private boolean isSubmittable(MemberDeathRecordStatus status) {
        return status == MemberDeathRecordStatus.NEW || status == MemberDeathRecordStatus.INCOMPLETE;
    }

    private MemberDeathRecordStatus parseStatus(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            throw new RuntimeException("Status is required");
        }

        String normalized = statusValue.trim()
                .toUpperCase()
                .replace("-", "_")
                .replace("PND", "PD");

        try {
            return MemberDeathRecordStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid status: " + statusValue);
        }
    }

    private String generateRecordId() {
        int year = LocalDate.now().getYear();
        String prefix = "MD-" + year + "-";

        return recordRepository.findLastRecordByPrefix(prefix)
                .map(lastRecord -> {
                    String lastNo = lastRecord.getRecordId();
                    int lastSeq = Integer.parseInt(lastNo.substring(lastNo.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    private String resolveBankName(Long bankId) {
        if (bankId == null) {
            return "-";
        }
        return bankRepository.findById(bankId)
                .map(bank -> bank.getName())
                .orElse("-");
    }

    private String resolveBranchName(Long branchId) {
        if (branchId == null) {
            return "-";
        }
        return branchRepository.findById(branchId)
                .map(branch -> branch.getName())
                .orElse("-");
    }

    private Long resolveCauseOfDeathId(String causeName, List<CauseOfDeath> causes) {
        if (causeName == null || causeName.isBlank()) {
            return null;
        }
        return causes.stream()
                .filter(cause -> causeName.equalsIgnoreCase(cause.getName())
                        || causeName.equalsIgnoreCase(cause.getCode()))
                .map(CauseOfDeath::getId)
                .findFirst()
                .orElse(null);
    }

    // Map-backed counterpart of resolveBankName/resolveBranchName, matching their
    // "-" fallback for a missing id or an id with no matching row.
    private String resolveName(Long id, Map<Long, String> namesById) {
        if (id == null) {
            return "-";
        }
        return namesById.getOrDefault(id, "-");
    }

    private String buildIdentification(Member member) {
        String type = member.getIdentification() != null ? member.getIdentification().name() : "";
        String number = member.getIdentificationNumber() != null ? member.getIdentificationNumber() : "";
        if (type.isBlank() && number.isBlank()) {
            return null;
        }
        return (type + " " + number).trim();
    }

    private boolean matchesStatuses(MemberDeathRecord record, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return true;
        }
        String status = record.getStatus() != null ? record.getStatus().name() : null;
        return statuses.stream()
                .map(this::normalizeStatusFilter)
                .anyMatch(candidate -> candidate.equalsIgnoreCase(status));
    }

    private String normalizeStatusFilter(String status) {
        return status.trim()
                .toUpperCase()
                .replace("-", "_")
                .replace("PND", "PD");
    }

    private boolean matchesInformedDateRange(MemberDeathRecord record, String fromDate, String toDate) {
        if (record.getInformedDate() == null) {
            return false;
        }
        LocalDate date = record.getInformedDate();
        if (fromDate != null && !fromDate.isBlank() && date.isBefore(LocalDate.parse(fromDate))) {
            return false;
        }
        if (toDate != null && !toDate.isBlank() && date.isAfter(LocalDate.parse(toDate))) {
            return false;
        }
        return true;
    }

    private boolean matchesSearch(MemberDeathRecord record, String searchKey) {
        if (searchKey == null || searchKey.isBlank()) {
            return true;
        }
        String key = searchKey.toLowerCase();
        Member member = record.getMember();
        return contains(record.getRecordId(), key)
                || contains(member != null ? member.getMemberId() : null, key)
                || contains(member != null ? member.getFullName() : null, key)
                || contains(member != null ? member.getNameWithInitials() : null, key)
                || contains(member != null ? member.getNic() : null, key)
                || contains(record.getCauseOfDeath(), key);
    }

    private int compareForSort(MemberDeathRecord left, MemberDeathRecord right, String sortBy, String sortOrder) {
        int result;
        if ("status".equalsIgnoreCase(sortBy)) {
            result = safeString(left.getStatus() != null ? left.getStatus().name() : null)
                    .compareToIgnoreCase(safeString(right.getStatus() != null ? right.getStatus().name() : null));
        } else if ("memberId".equalsIgnoreCase(sortBy)) {
            result = safeString(left.getMember() != null ? left.getMember().getMemberId() : null)
                    .compareToIgnoreCase(safeString(right.getMember() != null ? right.getMember().getMemberId() : null));
        } else {
            result = safeString(left.getInformedDate() != null ? left.getInformedDate().toString() : null)
                    .compareTo(safeString(right.getInformedDate() != null ? right.getInformedDate().toString() : null));
        }
        return "desc".equalsIgnoreCase(sortOrder) ? -result : result;
    }

    private String buildValidationMessage(boolean hasOutstandingLoans, boolean hasLoanObligations) {
        StringBuilder messageBuilder = new StringBuilder();
        if (hasOutstandingLoans) {
            messageBuilder.append("Member has outstanding loan balances");
        }
        if (hasLoanObligations) {
            if (!messageBuilder.isEmpty()) {
                messageBuilder.append(". ");
            }
            messageBuilder.append("Member has indirect loan obligations as nominee for another member's active loan");
        }
        return messageBuilder.toString();
    }

    private void deleteDocumentFileQuietly(MemberDeathDocument document) {
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            return;
        }
        try {
            s3Service.deleteFile(document.getFilePath());
        } catch (Exception ex) {
            System.err.println(
                    "Failed to delete S3 file for member death document "
                            + document.getId()
                            + ": "
                            + ex.getMessage()
            );
        }
    }

    // ------------------------------------------------------------------
    // Caller identity, district scoping and per-level authority.
    //
    // The authenticated principal is the User entity itself (see JwtFilter), so
    // it is read straight from the security context rather than passed in: who
    // is acting is a fact about the request, not an argument a caller may pick.
    // Same approach as TerminationService.
    // ------------------------------------------------------------------

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private String currentUsername() {
        User user = currentUser();
        return user != null ? user.getUsername() : null;
    }

    private Role currentRole() {
        User user = currentUser();
        return user != null ? user.getRole() : null;
    }

    /**
     * The set of districts the caller may see, or null for no restriction.
     *
     * A District Office user is pinned to their own district and their requested
     * locations are ignored. A District Office user with no assigned district gets
     * an empty set - scoped to nothing, never to everything.
     */
    private Set<String> resolveVisibleLocations(List<String> requestedLocations) {
        User user = currentUser();

        if (user != null && user.getRole() == Role.DISTRICT_OFFICE) {
            String assigned = user.getAssignedDistrict();
            if (assigned == null || assigned.isBlank()) {
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

    private boolean matchesLocation(MemberDeathRecord record, Set<String> effectiveLocations) {
        if (effectiveLocations == null) {
            return true;
        }
        return record.getSubmissionLocation() != null
                && effectiveLocations.contains(record.getSubmissionLocation());
    }

    /** The caller district, or null when they are not scoped to one. */
    private String callerAssignedDistrict() {
        User user = currentUser();
        if (user != null && user.getRole() == Role.DISTRICT_OFFICE) {
            return user.getAssignedDistrict();
        }
        return null;
    }

    private String resolveSubmissionLocationFor(Member member) {
        if (member.getSubmissionLocation() != null && !member.getSubmissionLocation().isBlank()) {
            return member.getSubmissionLocation();
        }
        return callerAssignedDistrict();
    }

    /** Blocks a District Office user from touching another district records. */
    private void assertCallerMayAccess(MemberDeathRecord record) {
        String district = callerAssignedDistrict();
        if (district == null) {
            return;
        }
        if (!district.equals(record.getSubmissionLocation())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This member death record belongs to another district");
        }
    }

    /** Same check for creation, before a record exists to check against. */
    private void assertCallerMayActFor(Member member) {
        String district = callerAssignedDistrict();
        if (district == null) {
            return;
        }
        if (!district.equals(member.getSubmissionLocation())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This member belongs to another district");
        }
    }

    private boolean currentUserHasInactiveRights() {
        Role role = currentRole();
        return role == Role.HEAD_OFFICE || role == Role.BOARD_SECRETARY || role == Role.SUPER_ADMIN;
    }

    /**
     * The record must be sitting at one of the three decision levels.
     *
     * @return the level it is sitting at, for stamping and auditing
     */
    private MemberDeathRecordStatus assertDecidableLevel(MemberDeathRecord record, String action) {
        MemberDeathRecordStatus status = record.getStatus();
        if (!DECISION_ROLE.containsKey(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Record cannot be " + action + " in status " + status);
        }
        return status;
    }

    /**
     * A decision belongs to the role that owns the level the record is sitting at.
     * SUPER_ADMIN always passes, so the flow stays exercisable before the committee
     * accounts exist.
     */
    private void assertMayDecideAtCurrentLevel(MemberDeathRecord record) {
        Role role = currentRole();
        if (role == Role.SUPER_ADMIN) {
            return;
        }

        Role required = DECISION_ROLE.get(record.getStatus());
        if (required == null || role != required) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This record is awaiting a decision from " + levelName(record.getStatus()));
        }
    }

    /**
     * The SRS separates the District Office clerk who raises a record (MMT18) from
     * the Authorized User who decides it (MMT22). Both map to DISTRICT_OFFICE here,
     * so segregation of duty is enforced by refusing to let the author decide their
     * own record rather than by a per-user rights flag.
     */
    private void assertNotSelfApproval(MemberDeathRecord record) {
        if (currentRole() == Role.SUPER_ADMIN) {
            return;
        }
        String caller = currentUsername();
        if (caller != null && caller.equals(record.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You raised this record, so it must be decided by another authorized user");
        }
    }

    /** Records who decided at which level, so the trail survives the status change. */
    private void stampDecision(MemberDeathRecord record, MemberDeathRecordStatus level) {
        String actor = currentUsername();
        LocalDateTime now = LocalDateTime.now();

        switch (level) {
            case SUBMITTED_FOR_APPROVAL -> {
                record.setLevel1DecidedBy(actor);
                record.setLevel1DecidedAt(now);
            }
            case DISTRICT_COMMITTEE -> {
                record.setLevel2DecidedBy(actor);
                record.setLevel2DecidedAt(now);
            }
            case PD_COMMITTEE -> {
                record.setLevel3DecidedBy(actor);
                record.setLevel3DecidedAt(now);
            }
            default -> {
                // Not a decision level; nothing to stamp.
            }
        }
    }

    private String levelName(MemberDeathRecordStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case SUBMITTED_FOR_APPROVAL -> "District Office";
            case DISTRICT_COMMITTEE -> "District Committee";
            case PD_COMMITTEE -> "P&D Committee";
            default -> status.name();
        };
    }

    /**
     * Concerns accumulate, they do not replace one another: MMT20 says the entered
     * information stays visible for everyone who later retrieves the record, so each
     * level appends its own attributed note.
     */
    private void appendConcern(MemberDeathRecord record, String concerns) {
        String trimmed = trimToNull(concerns);
        if (trimmed == null) {
            return;
        }

        String attribution = " - " + defaultString(currentUsername())
                + " @ " + LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String entry = trimmed + attribution;
        String existing = trimToNull(record.getConcernsIdentified());

        record.setConcernsIdentified(existing == null ? entry : existing + "\n" + entry);
    }

    /** Skips no-op transitions, so the audit trail only carries real changes. */
    private void auditMemberStatusChange(
            MemberDeathRecord record,
            MemberStatus oldStatus,
            MemberStatus newStatus,
            String remarks
    ) {
        if (oldStatus == newStatus) {
            return;
        }
        auditService.recordStatusChange(AuditService.MODULE_MEMBER_DEATH, record.getId(),
                "MEMBER_STATUS_CHANGE", oldStatus, newStatus, remarks);
    }

    private String formatTimestamp(LocalDateTime value) {
        return value != null ? value.format(DATE_TIME_FORMATTER) : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : "-";
    }

    private String firstNonBlank(String primary, String fallback) {
        String trimmedPrimary = trimToNull(primary);
        if (trimmedPrimary != null) {
            return trimmedPrimary;
        }
        return trimToNull(fallback);
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }
}
