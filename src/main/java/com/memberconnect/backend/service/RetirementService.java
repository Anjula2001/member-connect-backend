package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.dto.MemberRetirementRequestDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberSummaryDTO;
import com.memberconnect.backend.dto.RetirementRequestResponseDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.event.MemberRetiredEvent;
import com.memberconnect.backend.event.RetirementMarkedIncompleteEvent;
import com.memberconnect.backend.event.RetirementRejectedEvent;
import com.memberconnect.backend.enums.RetirementRequestStatus;
import com.memberconnect.backend.model.Loan;
import com.memberconnect.backend.model.LoanObligation;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.RetirementRequest;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.RetirementRequestRepository;

@Service
public class RetirementService {

    private static final Logger log = LoggerFactory.getLogger(RetirementService.class);

    private final MemberRepository memberRepository;
    private final RetirementRequestRepository requestRepository;
    private final LoanRepository loanRepository;
    private final LoanObligationRepository obligationRepository;
    private final DocumentService documentService;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberStatusHistoryService memberStatusHistoryService;

    public RetirementService(
            MemberRepository memberRepository,
            RetirementRequestRepository requestRepository,
            LoanRepository loanRepository,
            LoanObligationRepository obligationRepository,
            DocumentService documentService,
            CurrentUserService currentUserService,
            ApplicationEventPublisher eventPublisher,
            MemberStatusHistoryService memberStatusHistoryService) {
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.loanRepository = loanRepository;
        this.obligationRepository = obligationRepository;
        this.documentService = documentService;
        this.currentUserService = currentUserService;
        this.eventPublisher = eventPublisher;
        this.memberStatusHistoryService = memberStatusHistoryService;
    }

    // returns all retirement requests.
    public List<RetirementRequestResponseDTO> getAllRequests() {
        return requestRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public Member getMemberEntity(String memberId) {
        return memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
    }

    public MemberSummaryDTO getMemberSummary(String memberId) {
        Member member = getMemberEntity(memberId);

        return new MemberSummaryDTO(
                member.getMemberId(),
                member.getFullName(),
                member.getNameWithInitials(),
                member.getNic(),
                member.getStatus().name()

        );
    }

    // Validate member for retirement request (check loan)
    public MemberRetirementValidationDTO validateMemberForRetirement(String memberId) {
        List<Loan> ownLoans = loanRepository.findByMemberId(memberId);
        List<LoanObligation> obligations = obligationRepository.findByMemberId(memberId);

        boolean hasOutstandingLoans = !ownLoans.isEmpty();
        boolean hasLoanObligations = !obligations.isEmpty();

        double totalOutstandingBalance = ownLoans.stream()
                .mapToDouble(Loan::getBalance)
                .sum();

        boolean canSubmit = !hasOutstandingLoans && !hasLoanObligations;

        StringBuilder messageBuilder = new StringBuilder();

        if (hasOutstandingLoans) {
            messageBuilder.append("Member has outstanding loan balance");
        }

        if (hasLoanObligations) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append(" and ");
            }
            messageBuilder.append("Member has loan obligation");
        }

        MemberRetirementValidationDTO dto = new MemberRetirementValidationDTO();
        dto.setHasOutstandingLoans(hasOutstandingLoans);
        dto.setHasLoanObligations(hasLoanObligations);
        dto.setTotalOutstandingLoanBalance(totalOutstandingBalance);
        dto.setCanSubmit(canSubmit);
        dto.setMessage(messageBuilder.toString());

        return dto;
    }

    // generate request number
    private String generateRequestNo() {
        int year = LocalDate.now().getYear();
        String prefix = "R-" + year + "-";

        return requestRepository
                .findLastRequestByPrefix(prefix)
                .map(lastRequest -> {
                    String lastNo = lastRequest.getRequestNo();

                    int lastSeq = Integer.parseInt(
                            lastNo.substring(lastNo.lastIndexOf("-") + 1));

                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    public RetirementRequestResponseDTO saveRequest(String memberId, MemberRetirementRequestDTO dto) {

        // Get member
        Member member = getMemberEntity(memberId);

        // Validate member status
        if (member.getStatus() != MemberStatus.ACTIVE &&
                member.getStatus() != MemberStatus.RETIREMENT_REQUESTED) {
            throw new RuntimeException("Only ACTIVE or RETIREMENT_REQUESTED member can save retirement request");
        }

        // Validate required fields
        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            throw new RuntimeException("Requested Date is required");
        }

        if (dto.getEffectiveDate() == null || dto.getEffectiveDate().isBlank()) {
            throw new RuntimeException("Effective Date is required");
        }

        // Parse dates
        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());
        LocalDate effectiveDate = LocalDate.parse(dto.getEffectiveDate());

        LocalDate today = LocalDate.now();

        // Validate dates
        if (requestedDate.isAfter(today)) {
            throw new RuntimeException("Requested Date cannot be a future date");
        }

        if (effectiveDate.isAfter(today)) {
            throw new RuntimeException("Effective Date cannot be a future date");
        }

        // Check existing request (ONLY ONE ACTIVE REQUEST)
        RetirementRequest existingRequest = requestRepository
                .findByMemberId(memberId)
                .stream()
                .filter(r -> r.getStatus() != RetirementRequestStatus.INACTIVE)
                .findFirst()
                .orElse(null);

        RetirementRequest request;

        if (existingRequest != null) {

            // EDIT MODE
            request = existingRequest;

            // Prevent editing after submit/approval/rejection
            if (request.getStatus() == RetirementRequestStatus.SUBMITTED_FOR_APPROVAL) {
                throw new RuntimeException("Cannot edit after submission");
            }

            if (request.getStatus() == RetirementRequestStatus.APPROVED ||
                    request.getStatus() == RetirementRequestStatus.REJECTED) {
                throw new RuntimeException("Cannot edit approved or rejected request");
            }

        } else {

            // NEW MODE
            request = new RetirementRequest();
            request.setRequestNo(generateRequestNo());
            request.setMemberId(memberId);
            request.setStatus(RetirementRequestStatus.NEW);
            request.setCreatedAt(java.time.LocalDateTime.now());

            User currentUser = currentUserService != null ? currentUserService.current() : null;
            request.setCreatedBy(currentUser != null ? currentUser.getUsername() : null);

            // submission_location records WHICH DISTRICT OFFICE RAISED the request, not
            // which office administers the member - the member's own district is already
            // on the Member row and matchesLocation() falls back to it when this is null.
            //
            // So it is stamped only for a District Office user, from their assigned
            // district. A Super Admin (and any head-office role) has no district to act
            // on behalf of, and previously inherited the member's one, which made a
            // centrally-raised request look like Colombo or Kandy had raised it.
            String location = null;
            if (currentUser != null && currentUser.getRole() == Role.DISTRICT_OFFICE) {
                String assigned = currentUser.getAssignedDistrict();
                location = (assigned == null || assigned.isBlank()) ? null : assigned.trim();
            }
            request.setSubmissionLocation(location);
        }

        // update values
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        // Save request
        RetirementRequest saved = requestRepository.save(request);

        // Update member status
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.RETIREMENT_REQUESTED);
        memberRepository.save(member);
        memberStatusHistoryService.record(member, previousMemberStatus, member.getStatus(),
                requestedDate, "RETIREMENT_REQUESTED",
                "Retirement request " + saved.getRequestNo());

        return mapToResponse(saved);
    }

    public RetirementRequestResponseDTO submitRequest(String requestNo) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        MemberRetirementValidationDTO validation = validateMemberForRetirement(request.getMemberId());

        if (!validation.isCanSubmit()) {
            throw new RuntimeException("Cannot submit: " + validation.getMessage());
        }

        boolean allMandatoryUploaded = documentService.allMandatoryDocumentsUploaded(
                request.getRequestNo(),
                request.getMemberId(),
                "RETIREMENT");

        if (!allMandatoryUploaded) {
            throw new RuntimeException("Cannot submit. Mandatory documents are missing.");
        }

        request.setStatus(RetirementRequestStatus.SUBMITTED_FOR_APPROVAL);
        RetirementRequest saved = requestRepository.save(request);

        return mapToResponse(saved);
    }

    // get requestdata
    public RetirementRequestResponseDTO getRequestByRequestNo(String requestNo) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseGet(() -> {
                    try {
                        Long id = Long.parseLong(requestNo);
                        return requestRepository.findById(id).orElse(null);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });

        if (request == null) {
            throw new RuntimeException("Retirement request not found: " + requestNo);
        }

        return mapToResponse(request);
    }

    public RetirementRequestResponseDTO getRequestById(Long id) {
        return getRequestByRequestNo(String.valueOf(id));
    }

    // change request status
    public RetirementRequestResponseDTO changeRequestStatus(String requestNo, String status) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        RetirementRequestStatus newStatus;
        try {
            newStatus = RetirementRequestStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid retirement request status: " + status);
        }

        RetirementRequestStatus currentStatus = request.getStatus();

        if (currentStatus == newStatus) {
            return mapToResponse(request);
        }

        if (!isStatusTransitionAllowed(currentStatus, newStatus)) {
            throw new RuntimeException(
                    "Cannot change status from " + currentStatus + " to " + newStatus);
        }

        request.setStatus(newStatus);

        if (newStatus == RetirementRequestStatus.INACTIVE) {
            request.setIncompleteReason(null);
            request.setRejectReason(null);

            Member member = getMemberEntity(request.getMemberId());
            MemberStatus previousMemberStatus = member.getStatus();
            member.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(member);
            memberStatusHistoryService.record(member, previousMemberStatus, member.getStatus(),
                    null, "RETIREMENT_REQUEST_INACTIVE",
                    "Retirement request " + request.getRequestNo() + " made inactive");
        } else if (newStatus == RetirementRequestStatus.NEW ||
                newStatus == RetirementRequestStatus.INCOMPLETE ||
                newStatus == RetirementRequestStatus.SUBMITTED_FOR_APPROVAL) {
            request.setRejectReason(null);
            if (newStatus == RetirementRequestStatus.NEW) {
                request.setIncompleteReason(null);
            }

            Member member = getMemberEntity(request.getMemberId());
            MemberStatus previousMemberStatus = member.getStatus();
            member.setStatus(MemberStatus.RETIREMENT_REQUESTED);
            memberRepository.save(member);
            memberStatusHistoryService.record(member, previousMemberStatus, member.getStatus(),
                    request.getRequestedDate(), "RETIREMENT_REQUEST_REOPENED",
                    "Retirement request " + request.getRequestNo() + " set to " + newStatus);
        }

        RetirementRequest saved = requestRepository.save(request);
        return mapToResponse(saved);
    }

    // in view mode status transition
    private boolean isStatusTransitionAllowed(RetirementRequestStatus current, RetirementRequestStatus next) {
        switch (current) {
            case NEW:
                return next == RetirementRequestStatus.INACTIVE;
            case INCOMPLETE:
                return next == RetirementRequestStatus.NEW || next == RetirementRequestStatus.INACTIVE;
            case SUBMITTED_FOR_APPROVAL:
                return next == RetirementRequestStatus.NEW || next == RetirementRequestStatus.INACTIVE;
            case REJECTED:
                return next == RetirementRequestStatus.NEW || next == RetirementRequestStatus.INACTIVE;
            case INACTIVE:
                return next == RetirementRequestStatus.NEW;
            default:
                return false;
        }
    }

    /**
     * Marks a retirement request as incomplete (MMT14) and tells the member why.
     *
     * @Transactional is required, not decorative: the notification is delivered by a
     * @TransactionalEventListener bound to AFTER_COMMIT, and Spring silently discards
     * such an event when it is published with no transaction in progress. It also
     * makes the save and the publish one unit, so a member is never emailed about a
     * status change that failed to persist.
     */
    @Transactional
    public RetirementRequestResponseDTO markIncomplete(String requestNo, String reason) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason);

        RetirementRequest saved = requestRepository.save(request);

        eventPublisher.publishEvent(new RetirementMarkedIncompleteEvent(
                saved.getMemberId(),
                saved.getRequestNo(),
                reason
        ));

        return mapToResponse(saved);
    }

    /**
     * Approve a retirement request (MMT16) and complete the member's retirement.
     *
     * The member passes through RETIREMENT_APPROVED, the approved details go to the
     * Finance Module, and the member ends up RETIRED. In the real system the last
     * step is the Finance Module's own — it calls back once its activities are done.
     * The Finance Module is outside this project's scope, so
     * {@link #callFinanceModuleApi} stands in for it and the retirement completes in
     * one call.
     */
    public RetirementRequestResponseDTO approveRequest(String requestNo) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.APPROVED);
        RetirementRequest saved = requestRepository.save(request);

        Member member = getMemberEntity(request.getMemberId());
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.RETIREMENT_APPROVED);
        memberRepository.save(member);
        // The retirement's own effective date, not today: the member retires on the date
        // the request names, which is what a later "was the member active on X" asks about
        memberStatusHistoryService.record(member, previousMemberStatus, member.getStatus(),
                request.getEffectiveDate(), "RETIREMENT_APPROVED",
                "Retirement request " + saved.getRequestNo() + " approved");

        // The member stops here. MMT17 — handing the details to the Finance Module and
        // retiring the member — is a separate, deliberate step: see sendToFinanceModule.
        return mapToResponse(saved);
    }

    /**
     * MMT17 — hand one approved retirement to the Finance Module and complete the
     * member's retirement.
     *
     * Driven by a button rather than by the approval itself, mirroring the Grade 5
     * scholarship handoff. Only a member sitting at RETIREMENT_APPROVED qualifies, so
     * the same retirement cannot be sent twice.
     *
     * The Finance call is deliberately NOT wrapped in a try/catch. This is a user
     * action with a visible outcome: if it fails the exception surfaces in the UI and
     * the member stays RETIREMENT_APPROVED, ready to be retried.
     */
    @Transactional
    public RetirementRequestResponseDTO sendToFinanceModule(String requestNo) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        if (request.getStatus() != RetirementRequestStatus.APPROVED) {
            throw new RuntimeException(
                    "Only an approved retirement request can be sent to the Finance Module. "
                            + requestNo + " is " + request.getStatus() + ".");
        }

        Member member = getMemberEntity(request.getMemberId());

        if (member.getStatus() == MemberStatus.RETIRED) {
            throw new RuntimeException(
                    "Member " + member.getMemberId() + " is already retired.");
        }

        if (member.getStatus() != MemberStatus.RETIREMENT_APPROVED) {
            throw new RuntimeException(
                    "Member " + member.getMemberId() + " is " + member.getStatus()
                            + " and cannot be retired. Expected " + MemberStatus.RETIREMENT_APPROVED + ".");
        }

        callFinanceModuleApi(request, member);

        // Real Finance confirms this over an API of its own; the mock reports success
        // immediately, so the member is retired here.
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.RETIRED);
        memberRepository.save(member);
        // Mirrors TERMINATION_COMPLETED: the membership closes on the retirement's own
        // effective date, which is what a later "was the member active on X" asks about.
        memberStatusHistoryService.record(member, previousMemberStatus, member.getStatus(),
                request.getEffectiveDate(), "RETIREMENT_COMPLETED",
                "Retirement request " + request.getRequestNo() + " completed by Finance");

        // AFTER_COMMIT, so a member is only ever told their membership has ended once
        // RETIRED is durable. A Finance failure throws above this line, leaving the
        // member RETIREMENT_APPROVED and no email sent.
        eventPublisher.publishEvent(new MemberRetiredEvent(
                member.getMemberId(),
                request.getRequestNo()
        ));

        return mapToResponse(request);
    }

    /**
     * Mock stand-in for the Finance Module's API (MMT17).
     *
     * Logs the details that would be POSTed so the handoff is visible end to end
     * without a second system. Swap the body for a real HTTP call when the Finance
     * Module exists; nothing else in this class has to change. Throwing from here
     * leaves the member RETIREMENT_APPROVED, which is what the caller relies on.
     */
    private void callFinanceModuleApi(RetirementRequest request, Member member) {
        User sender = currentUserService != null ? currentUserService.current() : null;

        log.info("[FINANCE MODULE - MOCK API] retirement handed off: requestNo={} memberId={} "
                + "memberName={} nic={} requestedDate={} effectiveDate={} location={} sentBy={}",
                request.getRequestNo(),
                member.getMemberId(),
                member.getFullName(),
                member.getNic(),
                request.getRequestedDate(),
                request.getEffectiveDate(),
                request.getSubmissionLocation(),
                sender != null ? sender.getUsername() : null);
    }

    /**
     * Reject a retirement request (MMT16), return the member to ACTIVE and tell them why.
     *
     * @Transactional earns its place twice here. It makes the two saves one unit - a
     * request must never end up REJECTED while the member is left stranded in
     * RETIREMENT_REQUESTED - and it gives the AFTER_COMMIT notification listener a
     * transaction to bind to, without which Spring discards the event unsent.
     */
    @Transactional
    public RetirementRequestResponseDTO rejectRequest(String requestNo, String reason) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.REJECTED);
        request.setRejectReason(reason);

        RetirementRequest saved = requestRepository.save(request);

        Member member = getMemberEntity(request.getMemberId());
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);
        memberStatusHistoryService.record(member, previousMemberStatus, member.getStatus(),
                null, "RETIREMENT_REJECTED",
                "Retirement request " + saved.getRequestNo() + " rejected");

        eventPublisher.publishEvent(new RetirementRejectedEvent(
                saved.getMemberId(),
                saved.getRequestNo(),
                reason
        ));

        return mapToResponse(saved);
    }

    // Get retirement requests for a member
    public List<RetirementRequestResponseDTO> getRequestsByMember(String memberId) {
        return requestRepository.findByMemberId(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private RetirementRequestResponseDTO mapToResponse(RetirementRequest request) {
        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElse(null);

        boolean hasLoanBalance = loanRepository
                .existsByMemberIdAndBalanceGreaterThan(request.getMemberId(), 0.0);

        boolean hasIndirectObligations = obligationRepository
                .existsByMemberId(request.getMemberId());

        return buildResponse(request, member, hasLoanBalance, hasIndirectObligations);
    }

    private RetirementRequestResponseDTO buildResponse(
            RetirementRequest request,
            Member member,
            boolean hasLoanBalance,
            boolean hasIndirectObligations) {
        RetirementRequestResponseDTO dto = new RetirementRequestResponseDTO(
                request.getId(),
                request.getRequestNo(),
                request.getMemberId(),
                member != null ? member.getFullName() : null,
                member != null ? member.getNameAsInPayroll() : null,
                member != null ? member.getNameWithInitials() : null,
                member != null ? member.getNic() : null,
                request.getRequestedDate() != null ? request.getRequestedDate().toString() : null,
                request.getEffectiveDate() != null ? request.getEffectiveDate().toString() : null,
                request.getComment(),
                request.getStatus().name(),
                request.getIncompleteReason(),
                request.getRejectReason(),

                hasLoanBalance,
                hasIndirectObligations);

        dto.setSubmissionLocation(request.getSubmissionLocation());
        dto.setCreatedBy(request.getCreatedBy());
        dto.setCreatedAt(request.getCreatedAt() != null ? request.getCreatedAt().toString() : null);
        dto.setMemberStatus(member != null && member.getStatus() != null ? member.getStatus().name() : null);

        return dto;
    }

    // Update request details
    public RetirementRequestResponseDTO updateRequest(
            String requestNo,
            MemberRetirementRequestDTO dto) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        if (request.getStatus() == RetirementRequestStatus.SUBMITTED_FOR_APPROVAL ||
                request.getStatus() == RetirementRequestStatus.APPROVED ||
                request.getStatus() == RetirementRequestStatus.REJECTED) {
            throw new RuntimeException("Cannot edit submitted, approved or rejected request");
        }

        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            throw new RuntimeException("Requested Date is required");
        }

        if (dto.getEffectiveDate() == null || dto.getEffectiveDate().isBlank()) {
            throw new RuntimeException("Effective Date is required");
        }

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());
        LocalDate effectiveDate = LocalDate.parse(dto.getEffectiveDate());
        LocalDate today = LocalDate.now();

        if (requestedDate.isAfter(today)) {
            throw new RuntimeException("Requested Date cannot be a future date");
        }

        if (effectiveDate.isAfter(today)) {
            throw new RuntimeException("Effective Date cannot be a future date");
        }

        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        if (request.getStatus() == RetirementRequestStatus.INCOMPLETE) {
            request.setStatus(RetirementRequestStatus.NEW);
            request.setIncompleteReason(null);
        }

        RetirementRequest saved = requestRepository.save(request);

        return mapToResponse(saved);
    }

    // filtering rquests
    public List<RetirementRequestResponseDTO> searchRequests(
            List<String> locations,
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder
    ) {
        String pinnedLocation = currentUserService != null ? currentUserService.restrictedToLocation() : null;

        if (currentUserService != null && currentUserService.isLocationRestricted() && pinnedLocation == null) {
            return List.of();
        }

        List<String> effectiveLocations = resolveLocationFilter(locations, pinnedLocation);

        // Status and date filtering needs no extra queries, so it runs first and
        // shrinks the set the member lookup below has to cover.
        List<RetirementRequest> candidates = requestRepository.findAll()
                .stream()

                // status filter
                .filter(r -> statuses == null || statuses.isEmpty()
                        || statuses.contains(r.getStatus().name()))

                //date filter
                .filter(r -> {
                    if (r.getRequestedDate() == null)
                        return false;

                    LocalDate date = r.getRequestedDate();

                    if (fromDate != null && !fromDate.isBlank()) {
                        LocalDate from = LocalDate.parse(fromDate);
                        if (date.isBefore(from))
                            return false;
                    }

                    if (toDate != null && !toDate.isBlank()) {
                        LocalDate to = LocalDate.parse(toDate);
                        if (date.isAfter(to))
                            return false;
                    }

                    return true;
                })
                .toList();

        // One query for every member on the page. The map is shared by the search
        // filter, location filter, and response mapping, replacing a lookup per row.
        Map<String, Member> membersById = loadMembersByMemberId(
                candidates.stream().map(RetirementRequest::getMemberId).toList()
        );

        List<RetirementRequest> matches = candidates.stream()

                // location filter (matches effective locations, falling back to member.submissionLocation)
                .filter(r -> matchesLocation(r, membersById.get(r.getMemberId()), effectiveLocations))

                // search filter
                .filter(r -> {
                    if (searchKey == null || searchKey.isBlank())
                        return true;

                    String key = searchKey.toLowerCase();

                    Member member = membersById.get(r.getMemberId());

                    return contains(r.getMemberId(), key)
                            || (member != null && contains(member.getFullName(), key))
                            || (member != null && contains(member.getNameAsInPayroll(), key))
                            || (member != null && contains(member.getNameWithInitials(), key))
                            || (member != null && contains(member.getNic(), key));
                })

                // sorting
                .sorted((a, b) -> {
                    int result;

                    if ("status".equalsIgnoreCase(sortBy)) {
                        result = a.getStatus().name().compareToIgnoreCase(b.getStatus().name());
                    } else if ("memberId".equalsIgnoreCase(sortBy)) {
                        result = a.getMemberId().compareToIgnoreCase(b.getMemberId());
                    } else {
                        result = a.getRequestedDate().compareTo(b.getRequestedDate());
                    }

                    return "desc".equalsIgnoreCase(sortOrder) ? -result : result;
                })
                .toList();

        return mapToResponses(matches, membersById);
    }

    /**
     * Batch counterpart of mapToResponse: resolves the loan flags for a whole
     * page in two queries instead of two per row. The DTOs it produces are
     * identical to mapping each request individually.
     */
    private List<RetirementRequestResponseDTO> mapToResponses(
            List<RetirementRequest> requests,
            Map<String, Member> membersById) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<String> memberIds = requests.stream()
                .map(RetirementRequest::getMemberId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<String> membersWithLoanBalance = memberIds.isEmpty()
                ? Set.of()
                : new HashSet<>(loanRepository.findMemberIdsWithPositiveBalance(memberIds));
        Set<String> membersWithObligations = memberIds.isEmpty()
                ? Set.of()
                : new HashSet<>(obligationRepository.findMemberIdsWithObligations(memberIds));

        return requests.stream()
                .map(request -> buildResponse(
                        request,
                        membersById.get(request.getMemberId()),
                        membersWithLoanBalance.contains(request.getMemberId()),
                        membersWithObligations.contains(request.getMemberId())))
                .toList();
    }

    private Map<String, Member> loadMembersByMemberId(List<String> memberIds) {
        List<String> distinctIds = memberIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        return memberRepository.findByMemberIdIn(distinctIds)
                .stream()
                .collect(Collectors.toMap(
                        Member::getMemberId,
                        member -> member,
                        (first, second) -> first));
    }

    private List<String> resolveLocationFilter(List<String> requested, String pinnedLocation) {
        if (pinnedLocation != null) {
            return List.of(pinnedLocation);
        }
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = requested.stream()
                .filter(location -> location != null && !location.isBlank())
                .filter(location -> !"ALL".equalsIgnoreCase(location))
                .toList();
        return cleaned;
    }

    private boolean matchesLocation(RetirementRequest r, Member member, List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return true;
        }
        String location = r != null ? r.getSubmissionLocation() : null;
        if ((location == null || location.isBlank()) && member != null) {
            location = member.getSubmissionLocation();
        }
        if (location == null || location.isBlank()) {
            return false;
        }
        final String locToMatch = location;
        return locations.stream().anyMatch(loc -> loc.equalsIgnoreCase(locToMatch));
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }
}
