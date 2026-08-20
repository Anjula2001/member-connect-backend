package com.memberconnect.backend.service;

import com.memberconnect.backend.config.DeathDonationDocumentSeeder;
import com.memberconnect.backend.dto.DeathDonationDeceasedPopulateDTO;
import com.memberconnect.backend.dto.DeathDonationDocumentDTO;
import com.memberconnect.backend.dto.DeathDonationRelativeDTO;
import com.memberconnect.backend.dto.DeathDonationRequestDTO;
import com.memberconnect.backend.dto.FinanceDeathDonationHandoffDTO;
import com.memberconnect.backend.enums.DeathDonationRequestStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.event.DeathDonationApprovedEvent;
import com.memberconnect.backend.event.DeathDonationMarkedIncompleteEvent;
import com.memberconnect.backend.event.DeathDonationRejectedEvent;
import com.memberconnect.backend.model.DeathDonationDocument;
import com.memberconnect.backend.model.DeathDonationRelative;
import com.memberconnect.backend.model.DeathDonationRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.DeathDonationDocumentRepository;
import com.memberconnect.backend.repository.DeathDonationRelationshipRepository;
import com.memberconnect.backend.repository.DeathDonationRequestRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.RequiredDocumentRepository;
import com.memberconnect.backend.service.finance.FinanceDeathDonationClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Death Donations for Members (SRS Requirement 05, section 2, MMD01-MMD08).
 *
 * Roles mirror the SRS actors. Entry and editing belong to the District Office;
 * the three decision levels each belong to their own role, which is what stops
 * one clerk walking a request from submission to approval on their own. The
 * controller annotations are the outer gate; every check is repeated here at
 * runtime, because an annotation cannot see the record it is protecting.
 *
 * Same shape as MemberDeathRecordService deliberately - the two requirements
 * describe the same three-level ladder, and a reviewer who knows one should not
 * have to learn a second set of conventions to audit the other.
 */
@Service
@Transactional
@SuppressWarnings("null")
public class DeathDonationService {

    /**
     * Where a request goes when it is escalated rather than decided
     * (MMD05 -> MMD06 -> MMD07).
     */
    private static final Map<DeathDonationRequestStatus, DeathDonationRequestStatus> FORWARD_TRANSITIONS =
            Map.of(
                    DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL,
                            DeathDonationRequestStatus.DISTRICT_COMMITTEE,
                    DeathDonationRequestStatus.DISTRICT_COMMITTEE,
                            DeathDonationRequestStatus.PD_COMMITTEE
            );

    /**
     * Which role owns the decision at each level. This is the runtime backstop
     * behind the controller annotations, and the thing that actually stops one
     * clerk walking a request through all three levels alone.
     */
    private static final Map<DeathDonationRequestStatus, Role> DECISION_ROLE = Map.of(
            DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL, Role.DISTRICT_OFFICE,
            DeathDonationRequestStatus.DISTRICT_COMMITTEE, Role.DISTRICT_COMMITTEE,
            DeathDonationRequestStatus.PD_COMMITTEE, Role.PD_COMMITTEE
    );

    private static final Map<DeathDonationRequestStatus, String> LEVEL_NAME = Map.of(
            DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL, "the District Office",
            DeathDonationRequestStatus.DISTRICT_COMMITTEE, "the District Committee",
            DeathDonationRequestStatus.PD_COMMITTEE, "the Planning & Development Committee"
    );

    /**
     * Who may see a Death Donation Request at all (MMD02 / MMD03). The District
     * Office raises them, the three decision levels read them before deciding,
     * and Head Office oversees. Deliberately excludes ACCOUNTS,
     * SCHOLARSHIP_OFFICER and DEATH_DONATION_OFFICER: none of them is an actor
     * anywhere in SRS section 2.
     */
    private static final Set<Role> READ_ROLES = Set.of(
            Role.DISTRICT_OFFICE,
            Role.DISTRICT_COMMITTEE,
            Role.PD_COMMITTEE,
            Role.HEAD_OFFICE,
            Role.BOARD_SECRETARY,
            Role.SUPER_ADMIN
    );

    /** Only the District Office raises and edits requests (MMD01 / MMD04). */
    private static final Set<Role> ENTRY_ROLES = Set.of(Role.DISTRICT_OFFICE, Role.SUPER_ADMIN);

    /** "The user needs Inactive rights" (MMD04 status matrix, SRS p.24). */
    private static final Set<Role> INACTIVE_RIGHTS_ROLES =
            Set.of(Role.HEAD_OFFICE, Role.BOARD_SECRETARY, Role.SUPER_ADMIN);

    /**
     * The MMD04 matrix (SRS p.24), verbatim. APPROVED is absent on purpose: the
     * SRS table has no row for it, and by then the request has been handed to
     * the Finance Module, so walking it back here would desynchronise the two.
     */
    private static final Map<DeathDonationRequestStatus, Set<DeathDonationRequestStatus>>
            STATUS_CHANGE_MATRIX = buildStatusChangeMatrix();

    private static Map<DeathDonationRequestStatus, Set<DeathDonationRequestStatus>> buildStatusChangeMatrix() {
        Map<DeathDonationRequestStatus, Set<DeathDonationRequestStatus>> matrix =
                new EnumMap<>(DeathDonationRequestStatus.class);

        matrix.put(DeathDonationRequestStatus.NEW,
                Set.of(DeathDonationRequestStatus.INACTIVE));
        matrix.put(DeathDonationRequestStatus.INCOMPLETE,
                Set.of(DeathDonationRequestStatus.NEW, DeathDonationRequestStatus.INACTIVE));
        matrix.put(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL,
                Set.of(DeathDonationRequestStatus.NEW, DeathDonationRequestStatus.INACTIVE));
        matrix.put(DeathDonationRequestStatus.DISTRICT_COMMITTEE,
                Set.of(DeathDonationRequestStatus.NEW, DeathDonationRequestStatus.INACTIVE));
        matrix.put(DeathDonationRequestStatus.PD_COMMITTEE,
                Set.of(DeathDonationRequestStatus.NEW, DeathDonationRequestStatus.INACTIVE));
        matrix.put(DeathDonationRequestStatus.REJECTED,
                Set.of(DeathDonationRequestStatus.NEW, DeathDonationRequestStatus.INACTIVE));
        matrix.put(DeathDonationRequestStatus.INACTIVE,
                Set.of(DeathDonationRequestStatus.NEW));

        return matrix;
    }

    /**
     * Used only when the Supporting Documents master has no DEATH_DONATION rows.
     *
     * Without this the mandatory check would pass vacuously - allMatch over an
     * empty stream is true - and a request could be submitted with no death
     * certificate at all. The seeder normally fills the master; this is the
     * belt-and-braces for a database where it has not run.
     */
    private static final Set<String> FALLBACK_MANDATORY_DOCUMENT_TYPES = Set.of(
            "DEATH_CERTIFICATE",
            "NIC_COPY"
    );

    private final DeathDonationRequestRepository requestRepository;
    private final DeathDonationDocumentRepository documentRepository;
    private final MemberRepository memberRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final DeathDonationRelationshipRepository relationshipRepository;
    private final S3Service s3Service;
    private final DeathDonationEntitlementService entitlementService;
    private final FinanceDeathDonationClient financeClient;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public DeathDonationService(
            DeathDonationRequestRepository requestRepository,
            DeathDonationDocumentRepository documentRepository,
            MemberRepository memberRepository,
            RequiredDocumentRepository requiredDocumentRepository,
            DeathDonationRelationshipRepository relationshipRepository,
            S3Service s3Service,
            DeathDonationEntitlementService entitlementService,
            FinanceDeathDonationClient financeClient,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.memberRepository = memberRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.relationshipRepository = relationshipRepository;
        this.s3Service = s3Service;
        this.entitlementService = entitlementService;
        this.financeClient = financeClient;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Reads (MMD02 / MMD03)
    // ------------------------------------------------------------------

    public List<DeathDonationRequestDTO> getRequestsByMember(String memberId) {
        Set<String> visible = resolveVisibleLocations(null);

        return requestRepository.findByMember_MemberIdOrderByRequestedDateDesc(memberId)
                .stream()
                .filter(request -> matchesLocation(request, visible))
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * MMD02. {@code locations} is honoured for Head Office and committee users; a
     * District Office user is pinned to their own district regardless of what
     * they ask for.
     */
    public List<DeathDonationRequestDTO> searchRequests(
            List<String> locations,
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder
    ) {
        Set<String> visible = resolveVisibleLocations(locations);

        return requestRepository.findAllByOrderByRequestedDateDesc()
                .stream()
                .filter(request -> matchesLocation(request, visible))
                .map(this::mapToResponse)
                .filter(dto -> matchesStatuses(dto, statuses))
                .filter(dto -> matchesRequestedDateRange(dto, fromDate, toDate))
                .filter(dto -> matchesSearch(dto, searchKey))
                .sorted((left, right) -> compareForSort(left, right, sortBy, sortOrder))
                .toList();
    }

    public DeathDonationRequestDTO getRequestByRequestNo(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);
        return mapToResponse(request);
    }

    /** The active relationship master, for the MMD01 dropdown. */
    public List<String> getRelationshipOptions() {
        return relationshipRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(relationship -> relationship.getName())
                .toList();
    }

    // ------------------------------------------------------------------
    // Entry and editing (MMD01 / MMD04)
    // ------------------------------------------------------------------

    public DeathDonationRequestDTO saveRequest(String memberId, DeathDonationRequestDTO dto) {
        validateRequestDto(dto, false);

        DeathDonationRequest request;
        boolean isNew = false;

        if (dto.getRequestNo() != null && !dto.getRequestNo().isBlank()) {
            request = getRequestEntity(dto.getRequestNo());
            assertCallerMayAccess(request);
            memberId = request.getMember().getMemberId();

            if (!isEditable(request.getStatus())) {
                // SRS p.14: "Once submitted, these records cannot be edited.
                // (Except the Concerns Identified field)". Deciding users reach
                // this path from View Mode, so it must not require entry rights.
                assertMayEditConcerns(request);
                applyConcernsOnlyUpdate(request, dto);
                return mapToResponse(requestRepository.save(request));
            }

            assertMayEnterRequests();
        } else {
            assertMayEnterRequests();
            isNew = true;

            Member member = getActiveMember(memberId);
            request = new DeathDonationRequest();
            request.setRequestNo(generateRequestNo());
            request.setMember(member);
            request.setStatus(DeathDonationRequestStatus.NEW);
            request.setCreatedBy(currentUsername());

            // SRS p.12: "The Member can go to any District Office and request for
            // a Death Donation irrespective of the district of their working
            // address." The office that took the request therefore owns it, not
            // the district the member happens to work in.
            request.setSubmissionLocation(resolveSubmissionLocationFor(member));
        }

        applyRequestFields(request, dto);
        replaceRelatives(request, dto.getRelatives());

        DeathDonationRequest saved = requestRepository.save(request);

        // SRS 2.2.3: the Death Donation Details appear once the request has been
        // saved, so the figures are pulled the first time there is a record to
        // hang them on and refreshed on later saves. Values the operator has
        // edited by hand are never overwritten - see populateFromFinance.
        entitlementService.populateFromFinance(saved);
        saved = requestRepository.save(saved);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                isNew ? "CREATE" : "UPDATE",
                null,
                saved.getStatus().name(),
                "Death donation request " + saved.getRequestNo() + " for member " + memberId
        );

        return mapToResponse(saved);
    }

    public DeathDonationRequestDTO submitRequest(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);
        assertMayEnterRequests();

        if (!isSubmittable(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request cannot be submitted in status " + request.getStatus());
        }

        validateRequestDto(mapToResponse(request), true);
        validateMandatoryDocuments(request.getRequestNo());

        DeathDonationRequestStatus previous = request.getStatus();
        request.setStatus(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        request.setIncompleteReason(null);

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "SUBMIT",
                previous.name(),
                saved.getStatus().name(),
                "Death donation request " + requestNo + " submitted for approval"
        );

        return mapToResponse(saved);
    }

    public DeathDonationRequestDTO markIncomplete(String requestNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incomplete reason is required");
        }

        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);
        assertMayEnterRequests();

        if (!isSubmittable(request.getStatus())
                && request.getStatus() != DeathDonationRequestStatus.INCOMPLETE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request cannot be marked incomplete in status " + request.getStatus());
        }

        DeathDonationRequestStatus previous = request.getStatus();
        request.setStatus(DeathDonationRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason.trim());

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "MARK_INCOMPLETE",
                previous.name(),
                saved.getStatus().name(),
                "Reason: " + reason.trim()
        );

        // SRS p.15: marking a request incomplete tells the member why.
        eventPublisher.publishEvent(new DeathDonationMarkedIncompleteEvent(
                saved.getMember().getMemberId(), saved.getRequestNo(), reason.trim()));

        return mapToResponse(saved);
    }

    /**
     * MMD04 manual status change. The matrix is the SRS table on p.24; moving a
     * request to Inactive additionally needs inactive rights.
     */
    public DeathDonationRequestDTO changeStatus(String requestNo, String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);

        DeathDonationRequestStatus target;
        try {
            target = DeathDonationRequestStatus.valueOf(requestedStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown status: " + requestedStatus);
        }

        DeathDonationRequestStatus current = request.getStatus();
        Set<DeathDonationRequestStatus> allowed =
                STATUS_CHANGE_MATRIX.getOrDefault(current, Set.of());

        if (!allowed.contains(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request in status " + current + " cannot be changed to " + target);
        }

        if (target == DeathDonationRequestStatus.INACTIVE && !currentUserHasInactiveRights()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Making a death donation request inactive requires inactive rights");
        }

        request.setStatus(target);

        if (target == DeathDonationRequestStatus.NEW) {
            // Back to the start of the ladder: the previous outcome no longer
            // describes the record, and leaving it behind would show a stale
            // rejection reason next to a request that is open again.
            request.setRejectReason(null);
            request.setIncompleteReason(null);
            request.setLevel1DecidedBy(null);
            request.setLevel1DecidedAt(null);
            request.setLevel2DecidedBy(null);
            request.setLevel2DecidedAt(null);
            request.setLevel3DecidedBy(null);
            request.setLevel3DecidedAt(null);
        }

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "CHANGE_STATUS",
                current.name(),
                target.name(),
                "Manual status change on " + requestNo
        );

        return mapToResponse(saved);
    }

    /**
     * The "Concerns Identified" field, which SRS pp.21-22 keeps editable in View
     * Mode "for the users who can approve the record on any level".
     */
    public DeathDonationRequestDTO updateConcerns(String requestNo, String concerns) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);
        assertMayEditConcerns(request);

        request.setConcernsIdentified(trimToNull(concerns));
        return mapToResponse(requestRepository.save(request));
    }

    // ------------------------------------------------------------------
    // Decisions (MMD05 / MMD06 / MMD07)
    // ------------------------------------------------------------------

    public DeathDonationRequestDTO approveRequest(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);

        DeathDonationRequestStatus level = assertDecidableLevel(request, "approved");
        assertMayDecideAtCurrentLevel(request);
        assertNotSelfApproval(request);

        request.setStatus(DeathDonationRequestStatus.APPROVED);
        stampDecision(request, level);

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "APPROVE",
                level.name(),
                saved.getStatus().name(),
                "Approved at " + levelName(level)
        );

        // MMD08: hand the approved donation to Finance for disbursement.
        financeClient.sendDeathDonationApproved(buildFinanceHandoff(saved, level));

        eventPublisher.publishEvent(new DeathDonationApprovedEvent(
                saved.getMember().getMemberId(), saved.getRequestNo(), levelName(level)));

        return mapToResponse(saved);
    }

    public DeathDonationRequestDTO rejectRequest(String requestNo, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reject reason is required");
        }

        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);

        DeathDonationRequestStatus level = assertDecidableLevel(request, "rejected");
        assertMayDecideAtCurrentLevel(request);
        assertNotSelfApproval(request);

        request.setStatus(DeathDonationRequestStatus.REJECTED);
        request.setRejectReason(reason.trim());
        stampDecision(request, level);

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "REJECT",
                level.name(),
                saved.getStatus().name(),
                "Rejected at " + levelName(level) + ". Reason: " + reason.trim()
        );

        eventPublisher.publishEvent(new DeathDonationRejectedEvent(
                saved.getMember().getMemberId(), saved.getRequestNo(),
                reason.trim(), levelName(level)));

        return mapToResponse(saved);
    }

    /**
     * Escalate one level rather than deciding (MMD05 -> District Committee,
     * MMD06 -> P&D Committee). The SRS lets the escalating user record why in
     * the Concerns Identified field on the way past.
     */
    public DeathDonationRequestDTO forwardToNextLevel(String requestNo, String concerns) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);

        DeathDonationRequestStatus current = request.getStatus();
        DeathDonationRequestStatus next = FORWARD_TRANSITIONS.get(current);

        if (next == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request in status " + current + " cannot be forwarded");
        }

        assertMayDecideAtCurrentLevel(request);
        assertNotSelfApproval(request);

        if (concerns != null && !concerns.isBlank()) {
            request.setConcernsIdentified(concerns.trim());
        }

        request.setStatus(next);
        stampDecision(request, current);

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "FORWARD",
                current.name(),
                next.name(),
                "Forwarded from " + levelName(current) + " to " + levelName(next)
        );

        return mapToResponse(saved);
    }

    // ------------------------------------------------------------------
    // Death Donation Details (SRS 2.2.3)
    // ------------------------------------------------------------------

    /**
     * The SRS refresh button: takes whatever the three operator-editable inputs
     * currently hold and recalculates the rest of the entitlement from them.
     *
     * Open to the committee roles as well as the District Office, because the
     * SRS lets an authorised user adjust these amounts while the request is in
     * View Mode at their level.
     */
    public DeathDonationRequestDTO refreshDonationEntitlement(
            String requestNo,
            Integer monthsRemitted,
            BigDecimal receivedPast12Months,
            BigDecimal creditedToSpecialFixedAccount
    ) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);
        assertMayEditAmounts();

        if (request.getStatus() == DeathDonationRequestStatus.APPROVED
                || request.getStatus() == DeathDonationRequestStatus.REJECTED
                || request.getStatus() == DeathDonationRequestStatus.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Entitlement cannot be changed once the request is " + request.getStatus());
        }

        entitlementService.applyOverrides(
                request, monthsRemitted, receivedPast12Months, creditedToSpecialFixedAccount);

        DeathDonationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_DEATH_DONATION,
                saved.getId(),
                "REFRESH_ENTITLEMENT",
                null,
                String.valueOf(saved.getDisburseDonationAmount()),
                "Entitlement recalculated for " + requestNo
        );

        return mapToResponse(saved);
    }

    // ------------------------------------------------------------------
    // Close relatives grid and deceased lookup (MMD01)
    // ------------------------------------------------------------------

    public List<DeathDonationRelativeDTO> refreshRelatives(
            String deathCertificateNumber,
            String excludeRequestNo
    ) {
        if (deathCertificateNumber == null || deathCertificateNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Death Certificate Number is required");
        }

        return requestRepository
                .findByDeathCertificateNumberIgnoreCase(deathCertificateNumber.trim())
                .stream()
                .filter(request -> excludeRequestNo == null
                        || excludeRequestNo.isBlank()
                        || !request.getRequestNo().equalsIgnoreCase(excludeRequestNo.trim()))
                .map(request -> {
                    DeathDonationRelativeDTO relative = new DeathDonationRelativeDTO();
                    String relativeMemberId = request.getMember().getMemberId();
                    relative.setRelativeMemberId(relativeMemberId);
                    relative.setRelationshipToDeceased(request.getRelationshipToDeceased());
                    relative.setAutoPopulated(true);
                    relative.setRelativeMemberName(
                            request.getMember().getFullName() != null
                                    ? request.getMember().getFullName()
                                    : relativeMemberId
                    );
                    return relative;
                })
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                DeathDonationRelativeDTO::getRelativeMemberId,
                                relative -> relative,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));
    }

    public DeathDonationDeceasedPopulateDTO populateDeceasedMember(String deceasedMemberId) {
        if (deceasedMemberId == null || deceasedMemberId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Deceased Member ID is required");
        }

        Member deceased = memberRepository.findByMemberId(deceasedMemberId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Deceased member not found"));

        DeathDonationDeceasedPopulateDTO dto = new DeathDonationDeceasedPopulateDTO();
        dto.setDeceasedMemberId(deceased.getMemberId());
        dto.setDeceasedName(deceased.getFullName());
        dto.setDeceasedPlaceOfWork(resolvePlaceOfWork(deceased));
        return dto;
    }

    // ------------------------------------------------------------------
    // Documents (MMD01)
    // ------------------------------------------------------------------

    /**
     * The Required Documents grid: every configured document type for a death
     * donation, whether it is mandatory, and what has been uploaded against it.
     */
    public List<DeathDonationDocumentDTO> getRequiredDocuments(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);

        List<RequiredDocument> master = requiredDocumentRepository
                .findByApplicationType(DeathDonationDocumentSeeder.DEATH_DONATION);

        List<DeathDonationDocumentDTO> rows = new ArrayList<>();
        for (RequiredDocument required : master) {
            DeathDonationDocumentDTO dto = new DeathDonationDocumentDTO();
            dto.setRequestNo(requestNo);
            dto.setDocumentType(DeathDonationDocumentSeeder.toDocumentTypeCode(required.getDocumentName()));
            dto.setDocumentName(required.getDocumentName());
            dto.setMandatory(required.isMandatory());
            rows.add(dto);
        }
        return rows;
    }

    public DeathDonationDocumentDTO uploadDocument(
            String requestNo,
            String documentType,
            MultipartFile file
    ) throws IOException {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);
        assertMayEnterRequests();

        if (!isEditable(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Documents cannot be uploaded after submission");
        }

        if (documentType == null || documentType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String normalizedDocumentType = documentType.trim().toUpperCase();
        List<DeathDonationDocument> existing = documentRepository.findByRequest_RequestNoAndDocumentType(
                requestNo,
                normalizedDocumentType
        );
        for (DeathDonationDocument existingDocument : existing) {
            deleteDocumentFile(existingDocument);
            documentRepository.delete(existingDocument);
        }

        String fileKey = s3Service.uploadFile(file);

        DeathDonationDocument document = new DeathDonationDocument();
        document.setRequest(request);
        document.setRequestNo(request.getRequestNo());
        document.setDocumentType(normalizedDocumentType);
        document.setFileName(file.getOriginalFilename());
        document.setFileType(file.getContentType());
        document.setMimeType(file.getContentType());
        document.setFilePath(fileKey);
        document.setMandatory(mandatoryDocumentTypes().contains(normalizedDocumentType));
        document.setUploadedAt(LocalDateTime.now());

        return mapDocumentToDto(documentRepository.save(document));
    }

    public List<DeathDonationDocumentDTO> getDocuments(String requestNo) {
        DeathDonationRequest request = getRequestEntity(requestNo);
        assertCallerMayAccess(request);

        return documentRepository.findByRequest_RequestNo(requestNo)
                .stream()
                .map(this::mapDocumentToDto)
                .toList();
    }

    public void deleteDocument(Long documentId) {
        DeathDonationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        DeathDonationRequest request = document.getRequest();
        assertCallerMayAccess(request);
        assertMayEnterRequests();

        if (!isEditable(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Documents cannot be deleted after submission");
        }

        deleteDocumentFile(document);
        documentRepository.delete(document);
    }

    public byte[] downloadDocument(Long documentId) {
        DeathDonationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        assertCallerMayAccess(document.getRequest());
        return s3Service.downloadFile(document.getFilePath());
    }

    /** The file name and content type, so the download can be served correctly. */
    public DeathDonationDocument getDocumentEntity(Long documentId) {
        DeathDonationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        assertCallerMayAccess(document.getRequest());
        return document;
    }

    // ------------------------------------------------------------------
    // Field application and validation
    // ------------------------------------------------------------------

    private void applyRequestFields(DeathDonationRequest request, DeathDonationRequestDTO dto) {
        request.setRelationshipToDeceased(dto.getRelationshipToDeceased().trim());
        request.setRequestedDate(LocalDate.parse(dto.getRequestedDate()));
        request.setDeceasedMember(dto.isDeceasedMember());
        request.setDeceasedMemberId(
                dto.isDeceasedMember() ? trimToNull(dto.getDeceasedMemberId()) : null
        );
        request.setDeceasedName(dto.getDeceasedName().trim());
        request.setMaidenNameIfMarried(trimToNull(dto.getMaidenNameIfMarried()));
        request.setDeceasedDate(LocalDate.parse(dto.getDeceasedDate()));
        request.setDeathCertificateNumber(dto.getDeathCertificateNumber().trim());
        request.setDeceasedPlaceOfWork(trimToNull(dto.getDeceasedPlaceOfWork()));
        request.setConcernsIdentified(trimToNull(dto.getConcernsIdentified()));

        if (request.getStatus() == DeathDonationRequestStatus.INCOMPLETE) {
            request.setStatus(DeathDonationRequestStatus.NEW);
            request.setIncompleteReason(null);
        }
    }

    private void applyConcernsOnlyUpdate(DeathDonationRequest request, DeathDonationRequestDTO dto) {
        request.setConcernsIdentified(trimToNull(dto.getConcernsIdentified()));
    }

    /**
     * Rebuilds the close-relatives grid.
     *
     * The auto-populated half is derived server-side from the other requests
     * sharing this death certificate, never taken from the payload. SRS p.12:
     * "These Member records cannot be removed from the grid" - and a rule the
     * browser alone enforces is not a rule, since the previous version simply
     * replayed whatever list the client sent.
     *
     * Manually added rows still come from the payload, minus any that collide
     * with a derived one.
     */
    private void replaceRelatives(DeathDonationRequest request, List<DeathDonationRelativeDTO> relatives) {
        request.getRelatives().clear();

        Set<String> autoMemberIds = new LinkedHashSet<>();

        if (request.getDeathCertificateNumber() != null
                && !request.getDeathCertificateNumber().isBlank()) {

            for (DeathDonationRelativeDTO derived
                    : refreshRelatives(request.getDeathCertificateNumber(), request.getRequestNo())) {

                autoMemberIds.add(derived.getRelativeMemberId());
                request.getRelatives().add(
                        newRelative(request, derived.getRelativeMemberId(),
                                derived.getRelationshipToDeceased(), true));
            }
        }

        if (relatives == null) {
            return;
        }

        for (DeathDonationRelativeDTO relativeDto : relatives) {
            if (relativeDto.isAutoPopulated()) {
                // Derived above from the database; a client copy is ignored.
                continue;
            }
            if (relativeDto.getRelativeMemberId() == null || relativeDto.getRelativeMemberId().isBlank()) {
                continue;
            }
            if (relativeDto.getRelationshipToDeceased() == null
                    || relativeDto.getRelationshipToDeceased().isBlank()) {
                continue;
            }
            if (!autoMemberIds.add(relativeDto.getRelativeMemberId().trim())) {
                continue;
            }

            request.getRelatives().add(
                    newRelative(request, relativeDto.getRelativeMemberId().trim(),
                            relativeDto.getRelationshipToDeceased().trim(), false));
        }
    }

    private DeathDonationRelative newRelative(
            DeathDonationRequest request,
            String relativeMemberId,
            String relationship,
            boolean autoPopulated
    ) {
        DeathDonationRelative relative = new DeathDonationRelative();
        relative.setRequest(request);
        relative.setRelativeMemberId(relativeMemberId);
        relative.setRelationshipToDeceased(relationship);
        relative.setAutoPopulated(autoPopulated);
        return relative;
    }

    private void validateRequestDto(DeathDonationRequestDTO dto, boolean forSubmit) {
        if (dto.getRelationshipToDeceased() == null || dto.getRelationshipToDeceased().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Relationship to the deceased is required");
        }

        assertKnownRelationship(dto.getRelationshipToDeceased());

        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested Date is required");
        }

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());
        if (requestedDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Requested Date cannot be a future date");
        }

        if (dto.getDeceasedName() == null || dto.getDeceasedName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name of the deceased is required");
        }

        if (dto.getDeceasedDate() == null || dto.getDeceasedDate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deceased Date is required");
        }

        if (dto.getDeathCertificateNumber() == null || dto.getDeathCertificateNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Death Certificate Number is required");
        }

        if (dto.isDeceasedMember()) {
            if (dto.getDeceasedMemberId() == null || dto.getDeceasedMemberId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Deceased Member ID is required when deceased is a member");
            }
            if (!memberRepository.findByMemberId(dto.getDeceasedMemberId().trim()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Deceased Member ID is not valid");
            }
        }

        if (forSubmit && dto.getRelatives() != null) {
            for (DeathDonationRelativeDTO relative : dto.getRelatives()) {
                if (!relative.isAutoPopulated()
                        && (relative.getRelativeMemberId() == null
                            || relative.getRelationshipToDeceased() == null)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "All manually added relatives must have member ID and relationship");
                }
            }
        }
    }

    /**
     * The relationship must come from the master (MMD01). Skipped entirely when
     * the master is empty, so a database where the seeder has not run stays
     * usable rather than rejecting every save.
     */
    private void assertKnownRelationship(String relationship) {
        List<String> options = getRelationshipOptions();
        if (options.isEmpty()) {
            return;
        }

        boolean known = options.stream().anyMatch(option -> option.equalsIgnoreCase(relationship.trim()));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Relationship to the deceased must be one of: " + String.join(", ", options));
        }
    }

    /**
     * The mandatory document type codes, read from the Supporting Documents
     * master and falling back to the built-in pair when the master is empty.
     */
    private Set<String> mandatoryDocumentTypes() {
        Set<String> fromMaster = requiredDocumentRepository
                .findByApplicationType(DeathDonationDocumentSeeder.DEATH_DONATION)
                .stream()
                .filter(RequiredDocument::isMandatory)
                .map(required -> DeathDonationDocumentSeeder.toDocumentTypeCode(required.getDocumentName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return fromMaster.isEmpty() ? FALLBACK_MANDATORY_DOCUMENT_TYPES : fromMaster;
    }

    private void validateMandatoryDocuments(String requestNo) {
        for (String documentType : mandatoryDocumentTypes()) {
            if (!documentRepository.existsByRequest_RequestNoAndDocumentType(requestNo, documentType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mandatory document missing: " + formatDocumentType(documentType));
            }
        }
    }

    // ------------------------------------------------------------------
    // Finance handoff (MMD08)
    // ------------------------------------------------------------------

    private FinanceDeathDonationHandoffDTO buildFinanceHandoff(
            DeathDonationRequest request,
            DeathDonationRequestStatus level
    ) {
        FinanceDeathDonationHandoffDTO handoff = new FinanceDeathDonationHandoffDTO();

        handoff.setRequestNo(request.getRequestNo());
        handoff.setMemberId(request.getMember().getMemberId());
        handoff.setMemberNic(request.getMember().getNic());
        handoff.setDeceasedName(request.getDeceasedName());
        handoff.setDeceasedDate(String.valueOf(request.getDeceasedDate()));
        handoff.setDeathCertificateNumber(request.getDeathCertificateNumber());
        handoff.setRelationshipToDeceased(request.getRelationshipToDeceased());
        handoff.setRequestedDate(String.valueOf(request.getRequestedDate()));
        handoff.setApprovedAt(String.valueOf(LocalDateTime.now()));
        handoff.setApprovedBy(currentUsername());
        handoff.setApprovalLevel(levelName(level));

        handoff.setMonthsRemitted(request.getMonthsRemitted());
        handoff.setMaximumDonationAmount(request.getMaximumDonationAmount());
        handoff.setEligibleDonationAmount(request.getEligibleDonationAmount());
        handoff.setReceivedPast12Months(request.getReceivedPast12Months());
        handoff.setFuneralAccountNo(request.getFuneralAccountNo());
        handoff.setCreditedToSpecialFixedAccount(request.getCreditedToSpecialFixedAccount());
        handoff.setDisburseDonationAmount(request.getDisburseDonationAmount());

        return handoff;
    }

    // ------------------------------------------------------------------
    // Caller identity, district scoping and per-level authority.
    //
    // The authenticated principal is the User entity itself (see JwtFilter), so
    // it is read straight from the security context rather than passed in: who
    // is acting is a fact about the request, not an argument a caller may pick.
    // Same approach as MemberDeathRecordService and TerminationService.
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
     * locations are ignored. A District Office user with no assigned district
     * gets an empty set - scoped to nothing, never to everything.
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

    private boolean matchesLocation(DeathDonationRequest request, Set<String> effectiveLocations) {
        if (effectiveLocations == null) {
            return true;
        }
        return request.getSubmissionLocation() != null
                && effectiveLocations.contains(request.getSubmissionLocation());
    }

    /** The caller district, or null when they are not scoped to one. */
    private String callerAssignedDistrict() {
        User user = currentUser();
        if (user != null && user.getRole() == Role.DISTRICT_OFFICE) {
            return user.getAssignedDistrict();
        }
        return null;
    }

    /**
     * Where a new request is filed. The caller district first, because the SRS
     * lets a member raise a request at any office; the member own district is
     * only a fallback for callers who have none, such as SUPER_ADMIN.
     */
    private String resolveSubmissionLocationFor(Member member) {
        String district = callerAssignedDistrict();
        if (district != null && !district.isBlank()) {
            return district;
        }
        return member.getSubmissionLocation();
    }

    /** Blocks a District Office user from touching another district requests. */
    private void assertCallerMayAccess(DeathDonationRequest request) {
        assertMayReadRequests();

        String district = callerAssignedDistrict();
        if (district == null) {
            return;
        }
        if (!district.equals(request.getSubmissionLocation())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This death donation request belongs to another district");
        }
    }

    public void assertMayReadRequests() {
        Role role = currentRole();
        if (role == null || !READ_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to death donation requests");
        }
    }

    public void assertMayEnterRequests() {
        Role role = currentRole();
        if (role == null || !ENTRY_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the District Office may raise or edit death donation requests");
        }
    }

    /**
     * SRS pp.21-22 keeps Concerns Identified editable in View Mode for anyone who
     * can approve at any level; the entry role may also edit it while the request
     * is still theirs to work on.
     */
    private void assertMayEditConcerns(DeathDonationRequest request) {
        Role role = currentRole();
        if (role == Role.SUPER_ADMIN || role == Role.DISTRICT_OFFICE) {
            return;
        }
        if (role != null && DECISION_ROLE.containsValue(role)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only users who can decide this request may edit the concerns");
    }

    /**
     * SRS p.22: "Some of these values can be edited (in view mode) if the logged
     * in user has the authority to change the Death Donation values." That
     * authority sits with the three decision levels.
     */
    private void assertMayEditAmounts() {
        Role role = currentRole();
        if (role == Role.SUPER_ADMIN) {
            return;
        }
        if (role != null && DECISION_ROLE.containsValue(role)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not have the authority to change the death donation values");
    }

    private boolean currentUserHasInactiveRights() {
        Role role = currentRole();
        return role != null && INACTIVE_RIGHTS_ROLES.contains(role);
    }

    /**
     * The request must be sitting at one of the three decision levels.
     *
     * @return the level it is sitting at, for stamping and auditing
     */
    private DeathDonationRequestStatus assertDecidableLevel(DeathDonationRequest request, String action) {
        DeathDonationRequestStatus status = request.getStatus();
        if (!DECISION_ROLE.containsKey(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request cannot be " + action + " in status " + status);
        }
        return status;
    }

    /**
     * A decision belongs to the role that owns the level the request is sitting
     * at. SUPER_ADMIN always passes, so the flow stays exercisable before the
     * committee accounts exist.
     */
    private void assertMayDecideAtCurrentLevel(DeathDonationRequest request) {
        Role role = currentRole();
        if (role == Role.SUPER_ADMIN) {
            return;
        }

        Role required = DECISION_ROLE.get(request.getStatus());
        if (required == null || role != required) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This request is awaiting a decision from " + levelName(request.getStatus()));
        }
    }

    /**
     * The SRS separates the District Office clerk who raises a request (MMD01)
     * from the Authorized User who decides it (MMD05). Both map to
     * DISTRICT_OFFICE here, so segregation of duty is enforced by refusing to let
     * the author decide their own request rather than by a per-user rights flag.
     */
    private void assertNotSelfApproval(DeathDonationRequest request) {
        if (currentRole() == Role.SUPER_ADMIN) {
            return;
        }
        String caller = currentUsername();
        if (caller != null && caller.equals(request.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You raised this request, so it must be decided by another authorized user");
        }
    }

    /** Records who decided at which level, so the trail survives the status change. */
    private void stampDecision(DeathDonationRequest request, DeathDonationRequestStatus level) {
        String actor = currentUsername();
        LocalDateTime now = LocalDateTime.now();

        switch (level) {
            case SUBMITTED_FOR_APPROVAL -> {
                request.setLevel1DecidedBy(actor);
                request.setLevel1DecidedAt(now);
            }
            case DISTRICT_COMMITTEE -> {
                request.setLevel2DecidedBy(actor);
                request.setLevel2DecidedAt(now);
            }
            case PD_COMMITTEE -> {
                request.setLevel3DecidedBy(actor);
                request.setLevel3DecidedAt(now);
            }
            default -> {
                // Not a decision level; nothing to stamp.
            }
        }
    }

    private String levelName(DeathDonationRequestStatus status) {
        return LEVEL_NAME.getOrDefault(status, String.valueOf(status));
    }

    /** The MMD04 transitions this caller could actually make from here. */
    private List<String> allowedStatusChangesFor(DeathDonationRequest request) {
        Role role = currentRole();
        if (role == null) {
            return List.of();
        }

        boolean mayChange = role == Role.SUPER_ADMIN
                || role == Role.DISTRICT_OFFICE
                || INACTIVE_RIGHTS_ROLES.contains(role);

        if (!mayChange) {
            return List.of();
        }

        return STATUS_CHANGE_MATRIX.getOrDefault(request.getStatus(), Set.of())
                .stream()
                .filter(target -> target != DeathDonationRequestStatus.INACTIVE
                        || currentUserHasInactiveRights())
                .map(Enum::name)
                .sorted()
                .toList();
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private DeathDonationRequestDTO mapToResponse(DeathDonationRequest request) {
        Member member = request.getMember();

        DeathDonationRequestDTO dto = new DeathDonationRequestDTO();
        dto.setId(request.getId());
        dto.setRequestNo(request.getRequestNo());
        dto.setMemberId(member != null ? member.getMemberId() : null);
        dto.setMemberFullName(member != null ? member.getFullName() : null);
        dto.setMemberNameWithInitials(member != null ? member.getNameWithInitials() : null);
        dto.setMemberNameAsInPayroll(member != null ? member.getNameAsInPayroll() : null);
        dto.setMemberNic(member != null ? member.getNic() : null);
        dto.setMemberWorkingLocation(member != null ? member.getWorkingLocation() : null);
        dto.setMemberEducationalDistrict(member != null ? member.getEducationalDistrict() : null);
        dto.setStatus(request.getStatus().name());
        dto.setRelationshipToDeceased(request.getRelationshipToDeceased());
        dto.setRequestedDate(request.getRequestedDate().toString());
        dto.setDeceasedMember(request.isDeceasedMember());
        dto.setDeceasedMemberId(request.getDeceasedMemberId());
        dto.setDeceasedName(request.getDeceasedName());
        dto.setMaidenNameIfMarried(request.getMaidenNameIfMarried());
        dto.setDeceasedDate(request.getDeceasedDate().toString());
        dto.setDeathCertificateNumber(request.getDeathCertificateNumber());
        dto.setDeceasedPlaceOfWork(request.getDeceasedPlaceOfWork());
        dto.setConcernsIdentified(request.getConcernsIdentified());
        dto.setIncompleteReason(request.getIncompleteReason());
        dto.setRejectReason(request.getRejectReason());
        dto.setSubmissionLocation(request.getSubmissionLocation());
        dto.setCreatedBy(request.getCreatedBy());

        // One configured limit, read from DeathDonationConfig, instead of the
        // three-month constant this service used to carry.
        String warning = entitlementService.buildEligiblePeriodWarning(
                request.getDeceasedDate(), request.getRequestedDate());
        dto.setEligiblePeriodWarning(warning);
        dto.setDateRangeWarning(warning != null);

        dto.setMonthsRemitted(request.getMonthsRemitted());
        dto.setMonthsRemittedEdited(request.getMonthsRemittedEdited());
        dto.setMaximumDonationAmount(request.getMaximumDonationAmount());
        dto.setEligibleDonationAmount(request.getEligibleDonationAmount());
        dto.setReceivedPast12Months(request.getReceivedPast12Months());
        dto.setReceivedPast12MonthsEdited(request.getReceivedPast12MonthsEdited());
        dto.setFuneralAccountNo(request.getFuneralAccountNo());
        dto.setFuneralAccountCredited(request.getFuneralAccountCredited());
        dto.setFuneralAccountMaximum(request.getFuneralAccountMaximum());
        dto.setCreditedToSpecialFixedAccount(request.getCreditedToSpecialFixedAccount());
        dto.setCreditedToSpecialFixedEdited(request.getCreditedToSpecialFixedEdited());
        dto.setDisburseDonationAmount(request.getDisburseDonationAmount());
        dto.setDonationMultiplierApplied(request.getDonationMultiplierApplied());

        dto.setLevel1DecidedBy(request.getLevel1DecidedBy());
        dto.setLevel1DecidedAt(asText(request.getLevel1DecidedAt()));
        dto.setLevel2DecidedBy(request.getLevel2DecidedBy());
        dto.setLevel2DecidedAt(asText(request.getLevel2DecidedAt()));
        dto.setLevel3DecidedBy(request.getLevel3DecidedBy());
        dto.setLevel3DecidedAt(asText(request.getLevel3DecidedAt()));

        dto.setAllowedStatusChanges(allowedStatusChangesFor(request));

        dto.setRelatives(request.getRelatives().stream().map(relative -> {
            DeathDonationRelativeDTO relativeDto = new DeathDonationRelativeDTO();
            relativeDto.setId(relative.getId());
            relativeDto.setRelativeMemberId(relative.getRelativeMemberId());
            relativeDto.setRelationshipToDeceased(relative.getRelationshipToDeceased());
            relativeDto.setAutoPopulated(relative.isAutoPopulated());
            Member relativeMember = memberRepository.findByMemberId(relative.getRelativeMemberId()).orElse(null);
            relativeDto.setRelativeMemberName(
                    relativeMember != null ? relativeMember.getFullName() : relative.getRelativeMemberId()
            );
            return relativeDto;
        }).toList());

        return dto;
    }

    private String asText(LocalDateTime value) {
        return value != null ? value.toString() : null;
    }

    private DeathDonationDocumentDTO mapDocumentToDto(DeathDonationDocument document) {
        DeathDonationDocumentDTO dto = new DeathDonationDocumentDTO();
        dto.setId(document.getId());
        dto.setRequestNo(document.getRequestNo());
        dto.setDocumentType(document.getDocumentType());
        dto.setFileName(document.getFileName());
        dto.setFileType(document.getFileType());
        dto.setUploadedAt(document.getUploadedAt());
        return dto;
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private DeathDonationRequest getRequestEntity(String requestNo) {
        return requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Death donation request not found"));
    }

    private Member getActiveMember(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Death donation is only available for members with Active status");
        }

        return member;
    }

    private boolean isEditable(DeathDonationRequestStatus status) {
        return status == DeathDonationRequestStatus.NEW
                || status == DeathDonationRequestStatus.INCOMPLETE;
    }

    private boolean isSubmittable(DeathDonationRequestStatus status) {
        return status == DeathDonationRequestStatus.NEW
                || status == DeathDonationRequestStatus.INCOMPLETE;
    }

    private String resolvePlaceOfWork(Member member) {
        if (member.getWorkingLocation() != null && !member.getWorkingLocation().isBlank()) {
            return member.getWorkingLocation();
        }
        if (member.getWorkingLocationAddress() != null && !member.getWorkingLocationAddress().isBlank()) {
            return member.getWorkingLocationAddress();
        }
        if (member.getSalaryPayingOffice() != null && !member.getSalaryPayingOffice().isBlank()) {
            return member.getSalaryPayingOffice();
        }
        return null;
    }

    private String generateRequestNo() {
        int year = LocalDate.now().getYear();
        String prefix = "DD-" + year + "-";

        return requestRepository.findLastRequestByPrefix(prefix)
                .map(lastRequest -> {
                    String lastNo = lastRequest.getRequestNo();
                    int lastSeq = Integer.parseInt(lastNo.substring(lastNo.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesSearch(DeathDonationRequestDTO dto, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String key = search.trim().toLowerCase();
        return contains(dto.getRequestNo(), key)
                || contains(dto.getDeceasedName(), key)
                || contains(dto.getDeathCertificateNumber(), key)
                || contains(dto.getMemberId(), key)
                || contains(dto.getMemberFullName(), key)
                || contains(dto.getMemberNameWithInitials(), key)
                || contains(dto.getMemberNameAsInPayroll(), key)
                || contains(dto.getMemberNic(), key);
    }

    private boolean matchesStatuses(DeathDonationRequestDTO dto, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return true;
        }

        String requestStatus = dto.getStatus() == null ? "" : dto.getStatus().toUpperCase();
        return statuses.stream()
                .map(status -> status == null ? "" : status.trim().toUpperCase())
                .anyMatch(requestStatus::equals);
    }

    private boolean matchesRequestedDateRange(
            DeathDonationRequestDTO dto,
            String fromDate,
            String toDate
    ) {
        if ((fromDate == null || fromDate.isBlank()) && (toDate == null || toDate.isBlank())) {
            return true;
        }

        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            return false;
        }

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());

        if (fromDate != null && !fromDate.isBlank()) {
            LocalDate from = LocalDate.parse(fromDate);
            if (requestedDate.isBefore(from)) {
                return false;
            }
        }

        if (toDate != null && !toDate.isBlank()) {
            LocalDate to = LocalDate.parse(toDate);
            if (requestedDate.isAfter(to)) {
                return false;
            }
        }

        return true;
    }

    private int compareForSort(
            DeathDonationRequestDTO left,
            DeathDonationRequestDTO right,
            String sortBy,
            String sortOrder
    ) {
        int result;

        if ("deceasedDate".equalsIgnoreCase(sortBy)) {
            result = compareDates(left.getDeceasedDate(), right.getDeceasedDate());
        } else if ("status".equalsIgnoreCase(sortBy)) {
            result = compareStrings(left.getStatus(), right.getStatus());
        } else if ("memberId".equalsIgnoreCase(sortBy)) {
            result = compareStrings(left.getMemberId(), right.getMemberId());
        } else {
            result = compareDates(left.getRequestedDate(), right.getRequestedDate());
        }

        return "desc".equalsIgnoreCase(sortOrder) ? -result : result;
    }

    private int compareDates(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int compareStrings(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareToIgnoreCase(right);
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private void deleteDocumentFile(DeathDonationDocument document) {
        if (document.getFilePath() != null && !document.getFilePath().isBlank()) {
            s3Service.deleteFile(document.getFilePath());
        }
    }

    private String formatDocumentType(String documentType) {
        return switch (documentType) {
            case "DEATH_CERTIFICATE" -> "Death Certificate";
            case "NIC_COPY" -> "NIC Copy";
            default -> documentType.replace('_', ' ');
        };
    }
}
