package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
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

import com.memberconnect.backend.dto.FinanceTerminationHandoffDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberTerminationRequestDTO;
import com.memberconnect.backend.dto.TerminationMinorDisbursementDTO;
import com.memberconnect.backend.dto.TerminationReasonDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.event.TerminationApprovedEvent;
import com.memberconnect.backend.event.TerminationCompletedEvent;
import com.memberconnect.backend.event.TerminationMarkedIncompleteEvent;
import com.memberconnect.backend.event.TerminationRejectedEvent;
import com.memberconnect.backend.model.Loan;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.model.TerminationMinorDisbursement;
import com.memberconnect.backend.model.TerminationReason;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import com.memberconnect.backend.repository.TerminationMinorDisbursementRepository;
import com.memberconnect.backend.repository.TerminationReasonRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

@Service
@Transactional
public class TerminationService {

    // Single source of truth for which termination request statuses lock the
    // request against further edits - reused by document upload/delete gating
    // in DocumentController so the status list is never duplicated elsewhere.
    private static final Set<TerminationRequestStatus> DOCUMENT_LOCK_STATUSES = Set.of(
            TerminationRequestStatus.SUBMITTED_FOR_APPROVAL,
            TerminationRequestStatus.ADDED_TO_APPROVAL_LIST,
            TerminationRequestStatus.APPROVED,
            TerminationRequestStatus.REJECTED
    );

    /**
     * The MMT04 status-change matrix, transcribed from the SRS table in section
     * 2.2.4. Anything not listed here is not a permitted manual transition.
     *
     * Note what is deliberately absent: ADDED_TO_APPROVAL_LIST and APPROVED have
     * no entry at all. A request sitting on a board list is withdrawn by deleting
     * that list (MMT07), not by editing the request underneath it, and an
     * approved termination is undone by Finance, not by a status dropdown.
     */
    private static final Map<TerminationRequestStatus, Set<TerminationRequestStatus>>
            ALLOWED_STATUS_CHANGES = Map.of(
                    TerminationRequestStatus.NEW,
                    Set.of(TerminationRequestStatus.INACTIVE),

                    TerminationRequestStatus.INCOMPLETE,
                    Set.of(TerminationRequestStatus.NEW, TerminationRequestStatus.INACTIVE),

                    TerminationRequestStatus.SUBMITTED_FOR_APPROVAL,
                    Set.of(TerminationRequestStatus.NEW, TerminationRequestStatus.INACTIVE),

                    TerminationRequestStatus.REJECTED,
                    Set.of(TerminationRequestStatus.NEW, TerminationRequestStatus.INACTIVE),

                    TerminationRequestStatus.INACTIVE,
                    Set.of(TerminationRequestStatus.NEW)
            );

    /**
     * Statuses in which a termination request is still working its way through
     * the process. A member may only have one such request at a time, which is
     * what stops an INACTIVE request being revived alongside a live one.
     */
    private static final Set<TerminationRequestStatus> IN_FLIGHT_STATUSES = Set.of(
            TerminationRequestStatus.NEW,
            TerminationRequestStatus.INCOMPLETE,
            TerminationRequestStatus.SUBMITTED_FOR_APPROVAL,
            TerminationRequestStatus.ADDED_TO_APPROVAL_LIST
    );

    private final MemberRepository memberRepository;
    private final TerminationRequestRepository requestRepository;
    private final TerminationReasonRepository terminationReasonRepository;
    private final LoanRepository loanRepository;
    private final LoanObligationRepository obligationRepository;
    private final DocumentService documentService;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;
    private final TerminationMinorDisbursementRepository minorDisbursementRepository;
    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    public TerminationService(
            MemberRepository memberRepository,
            TerminationRequestRepository requestRepository,
            TerminationReasonRepository terminationReasonRepository,
            LoanRepository loanRepository,
            LoanObligationRepository obligationRepository,
            DocumentService documentService,
            MinorSavingsAccountRepository minorSavingsAccountRepository,
            TerminationMinorDisbursementRepository minorDisbursementRepository,
            BankRepository bankRepository,
            BranchRepository branchRepository,
            ApplicationEventPublisher eventPublisher,
            AuditService auditService
    ) {
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.terminationReasonRepository = terminationReasonRepository;
        this.loanRepository = loanRepository;
        this.obligationRepository = obligationRepository;
        this.documentService = documentService;
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
        this.minorDisbursementRepository = minorDisbursementRepository;
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    /**
     * MMT01 loan checks: outstanding own loans and indirect obligations (being the
     * nominee on another member's active loan). Both block Submit; neither blocks Save.
     *
     * This used to delegate to RetirementService.validateMemberForRetirement(), which
     * derives hasOutstandingLoans from {@code !ownLoans.isEmpty()} - i.e. ANY loan row,
     * including one already paid down to zero. That contradicted the same module's own
     * list screen, whose loan-balance icon comes from
     * LoanRepository.existsByMemberIdAndBalanceGreaterThan(memberId, 0.0). A member with
     * a settled loan therefore showed no warning icon in the list yet could not submit,
     * with a message claiming an outstanding balance of 0.00.
     *
     * Termination now answers the question itself, using balance > 0 on both sides.
     * RetirementService is deliberately left untouched: changing it would silently move
     * the retirement module's submit gate too, which is not this change's call to make.
     */
    public MemberRetirementValidationDTO validateMemberForTermination(String memberId) {
        double totalOutstandingBalance = loanRepository.findByMemberId(memberId)
                .stream()
                .mapToDouble(Loan::getBalance)
                .filter(balance -> balance > 0)
                .sum();

        boolean hasOutstandingLoans = loanRepository.hasOutstandingLoan(memberId);
        boolean hasLoanObligations = obligationRepository.existsByMemberId(memberId);

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
        dto.setCanSubmit(!hasOutstandingLoans && !hasLoanObligations);
        dto.setMessage(messageBuilder.toString());

        return dto;
    }

    /**
     * Server-side gate for supporting-document upload/delete. NEW and INCOMPLETE
     * requests remain editable; once a request reaches any status in
     * DOCUMENT_LOCK_STATUSES its documents can no longer be modified, regardless
     * of what the caller passes - this cannot be bypassed by calling the upload
     * or delete endpoints directly.
     */
    public void assertDocumentsEditable(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        // DocumentController carries no role annotations of its own - its paths are
        // shared by every module - so the termination-specific authority is asserted
        // here. Supporting documents are part of raising a request (MMT01), which the
        // spec gives to the District Office, and they gate Submit: any authenticated
        // role could otherwise delete the mandatory documents out from under a request.
        assertCallerIsRequestEntryRole();
        assertCallerMayAccess(request);

        if (DOCUMENT_LOCK_STATUSES.contains(request.getStatus())) {
            throw new RuntimeException(
                    "Cannot modify documents: termination request is already "
                            + request.getStatus().name()
            );
        }
    }

    /**
     * A member's termination requests, filtered to what the caller is entitled to
     * see. A District Office user reaching this by member id used to receive
     * another district's requests in full; the list screen never showed them, so
     * the only place that could be fixed is here.
     */
    public List<TerminationRequestResponseDTO> getRequestsByMember(String memberId) {
        String assignedDistrict = callerAssignedDistrict();

        return requestRepository.findByMemberId(memberId)
                .stream()
                .filter(request -> assignedDistrict == null
                        || assignedDistrict.equals(request.getSubmissionLocation()))
                .map(this::mapToResponse)
                .toList();
    }

    public TerminationRequestResponseDTO saveRequest(String memberId, MemberTerminationRequestDTO dto) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        assertCallerMayActFor(member);

        if (member.getStatus() != MemberStatus.ACTIVE &&
            member.getStatus() != MemberStatus.TERMINATION_REQUESTED) {
            throw new RuntimeException("Only ACTIVE members can save termination requests");
        }

        validateRequestDto(dto);

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());
        LocalDate effectiveDate = LocalDate.parse(dto.getEffectiveDate());
        LocalDate today = LocalDate.now();

        if (requestedDate.isAfter(today)) {
            throw new RuntimeException("Requested Date cannot be a future date");
        }

        if (effectiveDate.isAfter(today)) {
            throw new RuntimeException("Effective Date cannot be a future date");
        }

        TerminationRequest existingRequest = requestRepository
                .findByMemberId(memberId)
                .stream()
                .filter(r -> r.getStatus() != TerminationRequestStatus.INACTIVE)
                .findFirst()
                .orElse(null);

        TerminationRequest request;
        boolean isNewRequest = existingRequest == null;
        String beforeSnapshot = existingRequest != null ? describeRequest(existingRequest) : null;

        if (existingRequest != null) {
            request = existingRequest;
            assertCallerMayAccess(request);

            if (request.getStatus() == TerminationRequestStatus.SUBMITTED_FOR_APPROVAL ||
                request.getStatus() == TerminationRequestStatus.ADDED_TO_APPROVAL_LIST) {
                throw new RuntimeException("Cannot edit after submission");
            }

            if (request.getStatus() == TerminationRequestStatus.APPROVED ||
                request.getStatus() == TerminationRequestStatus.REJECTED) {
                throw new RuntimeException("Cannot edit approved or rejected request");
            }
        } else {
            request = new TerminationRequest();
            request.setRequestNo(generateRequestNo());
            request.setMemberId(memberId);
            request.setStatus(TerminationRequestStatus.NEW);
            // Stamped once, at creation, from the member's administering office,
            // falling back to the district of the user raising it.
            request.setSubmissionLocation(resolveSubmissionLocationFor(member));
        }

        applyTerminationReason(request, dto);
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        replaceMinorDisbursements(request, memberId, dto.getMinorDisbursements());

        TerminationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                isNewRequest ? "CREATE_REQUEST" : "UPDATE_REQUEST",
                beforeSnapshot,
                describeRequest(saved),
                "Termination request " + saved.getRequestNo() + " for member " + memberId
        );

        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.TERMINATION_REQUESTED);
        memberRepository.save(member);
        auditMemberStatusChange(saved, previousMemberStatus, member.getStatus(), "Termination request saved");

        return mapToResponse(saved);
    }

    /**
     * A one-line, human-readable snapshot of the fields MMT01 lets the District
     * Office edit. Used as the before/after value on audit rows so a reviewer can
     * see what an edit actually changed without diffing two entities by hand.
     */
    private String describeRequest(TerminationRequest request) {
        return "status=" + request.getStatus()
                + ", reason=" + request.getTerminationReason()
                + ", requestedDate=" + request.getRequestedDate()
                + ", effectiveDate=" + request.getEffectiveDate()
                + ", comment=" + request.getComment()
                + ", minorDisbursements=" + request.getMinorDisbursements().size();
    }

    /**
     * Records a member's status moving as a consequence of a termination action.
     *
     * Filed against the termination request rather than the member so the whole
     * story of one termination reads back as a single reference id, and skipped
     * entirely when the status did not actually move - an audit trail full of
     * "ACTIVE -> ACTIVE" rows is harder to read, not more complete.
     */
    private void auditMemberStatusChange(
            TerminationRequest request,
            MemberStatus oldStatus,
            MemberStatus newStatus,
            String remarks
    ) {
        if (oldStatus == newStatus) {
            return;
        }

        auditService.recordStatusChange(
                AuditService.MODULE_TERMINATION,
                request.getId(),
                "MEMBER_STATUS_CHANGE",
                oldStatus,
                newStatus,
                remarks + " (member " + request.getMemberId()
                        + ", request " + request.getRequestNo() + ")"
        );
    }

    public TerminationRequestResponseDTO updateRequest(
            String requestNo,
            MemberTerminationRequestDTO dto
    ) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        assertCallerMayAccess(request);

        if (request.getStatus() == TerminationRequestStatus.SUBMITTED_FOR_APPROVAL ||
            request.getStatus() == TerminationRequestStatus.ADDED_TO_APPROVAL_LIST ||
            request.getStatus() == TerminationRequestStatus.APPROVED ||
            request.getStatus() == TerminationRequestStatus.REJECTED) {
            throw new RuntimeException("Cannot edit submitted, approved or rejected request");
        }

        validateRequestDto(dto);

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());
        LocalDate effectiveDate = LocalDate.parse(dto.getEffectiveDate());
        LocalDate today = LocalDate.now();

        if (requestedDate.isAfter(today)) {
            throw new RuntimeException("Requested Date cannot be a future date");
        }

        if (effectiveDate.isAfter(today)) {
            throw new RuntimeException("Effective Date cannot be a future date");
        }

        String beforeSnapshot = describeRequest(request);

        applyTerminationReason(request, dto);
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        replaceMinorDisbursements(request, request.getMemberId(), dto.getMinorDisbursements());

        if (request.getStatus() == TerminationRequestStatus.INCOMPLETE) {
            request.setStatus(TerminationRequestStatus.NEW);
            request.setIncompleteReason(null);
        }

        TerminationRequest saved = requestRepository.save(request);

        auditService.record(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                "UPDATE_REQUEST",
                beforeSnapshot,
                describeRequest(saved),
                "Termination request " + saved.getRequestNo() + " edited"
        );

        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO submitRequest(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        assertCallerMayAccess(request);

        MemberRetirementValidationDTO validation = validateMemberForTermination(request.getMemberId());

        if (!validation.isCanSubmit()) {
            throw new RuntimeException("Cannot submit: " + validation.getMessage());
        }

        boolean allMandatoryUploaded = documentService.allMandatoryDocumentsUploaded(
                request.getRequestNo(),
                request.getMemberId(),
                "TERMINATION"
        );

        if (!allMandatoryUploaded) {
            throw new RuntimeException("Cannot submit. Mandatory documents are missing.");
        }

        validateMinorDisbursementsForSubmit(request);

        TerminationRequestStatus previousStatus = request.getStatus();
        request.setStatus(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
        TerminationRequest saved = requestRepository.save(request);

        auditService.recordStatusChange(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                "SUBMIT_REQUEST",
                previousStatus,
                saved.getStatus(),
                "Termination request " + saved.getRequestNo() + " submitted for board approval"
        );

        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO markIncomplete(String requestNo, String reason) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        assertCallerMayAccess(request);

        TerminationRequestStatus previousStatus = request.getStatus();
        request.setStatus(TerminationRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason);

        TerminationRequest saved = requestRepository.save(request);

        auditService.recordStatusChange(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                "MARK_INCOMPLETE",
                previousStatus,
                saved.getStatus(),
                "Reason: " + saved.getIncompleteReason()
        );

        // The member must be told by SMS and email, with the reason (SRS steps 5-7).
        // Publishing an event rather than notifying inline keeps this method's
        // contract unchanged: TerminationNotificationListener is bound to
        // AFTER_COMMIT, so nothing is sent unless this transaction commits, and a
        // failure to notify can never roll back the INCOMPLETE status.
        eventPublisher.publishEvent(new TerminationMarkedIncompleteEvent(
                saved.getMemberId(),
                saved.getRequestNo(),
                saved.getIncompleteReason()
        ));

        return mapToResponse(saved);
    }

    /**
     * Records a board approval for a single termination request (SRS MMT09).
     *
     * The member is moved to TERMINATION_APPROVED, not TERMINATED. The SRS is
     * explicit that the board's approval only stops the monthly remittance; the
     * membership itself is not closed until the Finance Module has cleared every
     * savings account and reports back (MMT11). Setting TERMINATED here would
     * claim work that has not happened and leave Finance nothing to complete.
     */
    public TerminationRequestResponseDTO approveRequest(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        assertAwaitingBoardDecision(request);

        TerminationRequestStatus previousStatus = request.getStatus();
        request.setStatus(TerminationRequestStatus.APPROVED);
        request.setRejectReason(null);
        TerminationRequest saved = requestRepository.save(request);

        auditService.recordStatusChange(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                "APPROVE_REQUEST",
                previousStatus,
                saved.getStatus(),
                "Board approved termination request " + saved.getRequestNo()
                        + "; handed to Finance for account closing"
        );

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.TERMINATION_APPROVED);
        memberRepository.save(member);
        auditMemberStatusChange(saved, previousMemberStatus, member.getStatus(), "Board approval");

        // Hands the member to the Finance Module for account closing (MMT11).
        // Published rather than called inline so the outbound HTTP call happens
        // after commit - a Finance outage must not roll back a board decision
        // that has already been taken.
        eventPublisher.publishEvent(new TerminationApprovedEvent(
                saved.getMemberId(),
                saved.getRequestNo()
        ));

        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO rejectRequest(String requestNo, String reason) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        assertAwaitingBoardDecision(request);

        TerminationRequestStatus previousStatus = request.getStatus();
        request.setStatus(TerminationRequestStatus.REJECTED);
        request.setRejectReason(reason);

        TerminationRequest saved = requestRepository.save(request);

        auditService.recordStatusChange(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                "REJECT_REQUEST",
                previousStatus,
                saved.getStatus(),
                "Board rejected termination request " + saved.getRequestNo()
                        + "; reason: " + saved.getRejectReason()
        );

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));
        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);
        auditMemberStatusChange(saved, previousMemberStatus, member.getStatus(), "Board rejection");

        // SRS MMT09: the member is told by SMS and email, with the reason.
        eventPublisher.publishEvent(new TerminationRejectedEvent(
                saved.getMemberId(),
                saved.getRequestNo(),
                saved.getRejectReason()
        ));

        return mapToResponse(saved);
    }

    /**
     * Completes a termination once the Finance Module reports that it has closed
     * the member's accounts (SRS MMT11).
     *
     * This is the only path to MemberStatus.TERMINATED. The SRS assigns that
     * transition to Finance, not to the board, because it asserts that every
     * savings account has actually been cleared.
     *
     * Written to be safely retryable: Finance is an external caller, so a repeat
     * of a call that already succeeded returns the current state rather than
     * failing, and notifies only once.
     */
    public TerminationRequestResponseDTO completeTermination(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found: " + requestNo));

        if (request.getStatus() != TerminationRequestStatus.APPROVED) {
            throw new RuntimeException(
                    "Termination request " + requestNo + " cannot be completed from status "
                            + request.getStatus() + ". Only board-approved requests can be completed."
            );
        }

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member.getStatus() == MemberStatus.TERMINATED) {
            // Already done on an earlier call. Returning quietly keeps Finance's
            // retries harmless; re-notifying would tell the member twice.
            return mapToResponse(request);
        }

        if (member.getStatus() != MemberStatus.TERMINATION_APPROVED) {
            throw new RuntimeException(
                    "Member " + member.getMemberId() + " cannot be terminated from status "
                            + member.getStatus() + ". Expected TERMINATION_APPROVED."
            );
        }

        MemberStatus previousMemberStatus = member.getStatus();
        member.setStatus(MemberStatus.TERMINATED);
        memberRepository.save(member);

        // MMT11. Two rows, because two distinct facts are being asserted: that
        // Finance reported completion, and that the membership consequently closed.
        auditService.record(
                AuditService.MODULE_TERMINATION,
                request.getId(),
                "FINANCE_COMPLETION",
                null,
                "COMPLETED",
                "Finance Module confirmed all accounts closed for request "
                        + request.getRequestNo()
        );
        auditMemberStatusChange(
                request,
                previousMemberStatus,
                member.getStatus(),
                "Finance completion"
        );

        eventPublisher.publishEvent(new TerminationCompletedEvent(
                member.getMemberId(),
                request.getRequestNo()
        ));

        return mapToResponse(request);
    }

    /**
     * Manual status change on a termination request (SRS MMT04).
     *
     * Two things are enforced here rather than in the UI, because the UI is not
     * where authority lives: the transition must appear in the SRS matrix, and
     * anything involving INACTIVE requires the "Inactive rights" the spec
     * reserves for Head Office and above.
     *
     * The member's own status is kept in step automatically - deactivating a
     * request returns the member to Active (spec 2.2.4), and reviving one puts
     * them back to Termination Requested. Leaving that to the caller is how a
     * member ends up stuck in TERMINATION_REQUESTED with no live request.
     */
    public TerminationRequestResponseDTO changeStatus(String requestNo, String targetStatusName) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found: " + requestNo));

        assertCallerMayAccess(request);

        TerminationRequestStatus targetStatus = parseStatus(targetStatusName);
        TerminationRequestStatus currentStatus = request.getStatus();

        if (currentStatus == targetStatus) {
            throw new RuntimeException("Termination request " + requestNo + " is already " + targetStatus);
        }

        if (!ALLOWED_STATUS_CHANGES.getOrDefault(currentStatus, Set.of()).contains(targetStatus)) {
            throw new RuntimeException(
                    "Termination request " + requestNo + " cannot be changed from "
                            + currentStatus + " to " + targetStatus + "."
            );
        }

        // Both directions are gated, not just deactivation. An INACTIVE request
        // was retired by someone holding that right, and reviving it undoes that
        // decision - which should take the same authority. This matches
        // MemberApplicationService.updateStatus, which gates set and clear alike.
        boolean touchesInactive = targetStatus == TerminationRequestStatus.INACTIVE
                || currentStatus == TerminationRequestStatus.INACTIVE;

        if (touchesInactive && !currentUserHasInactiveRights()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have rights to change this termination request to or from Inactive."
            );
        }

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (targetStatus == TerminationRequestStatus.NEW) {
            assertNoOtherInFlightRequest(request);
        }

        request.setStatus(targetStatus);

        if (targetStatus == TerminationRequestStatus.NEW) {
            // Reasons describe a state the request has just left; carrying them
            // forward would show a stale "Incomplete: ..." banner on a fresh one.
            request.setIncompleteReason(null);
            request.setRejectReason(null);
        }

        TerminationRequest saved = requestRepository.save(request);

        auditService.recordStatusChange(
                AuditService.MODULE_TERMINATION,
                saved.getId(),
                "STATUS_CHANGE",
                currentStatus,
                targetStatus,
                "Manual status change on " + saved.getRequestNo() + " (MMT04)"
        );

        MemberStatus previousMemberStatus = member.getStatus();

        if (targetStatus == TerminationRequestStatus.INACTIVE) {
            member.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(member);
        } else if (targetStatus == TerminationRequestStatus.NEW) {
            member.setStatus(MemberStatus.TERMINATION_REQUESTED);
            memberRepository.save(member);
        }

        auditMemberStatusChange(
                saved,
                previousMemberStatus,
                member.getStatus(),
                "Manual request status change to " + targetStatus
        );

        return mapToResponse(saved);
    }

    private TerminationRequestStatus parseStatus(String targetStatusName) {
        if (targetStatusName == null || targetStatusName.isBlank()) {
            throw new RuntimeException("A target status is required");
        }

        try {
            return TerminationRequestStatus.valueOf(targetStatusName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown termination request status: " + targetStatusName);
        }
    }

    /**
     * A member may only have one termination request in flight. Reviving an
     * INACTIVE request while another is already running would give the member two
     * competing requests, and saveRequest would then pick between them arbitrarily.
     */
    private void assertNoOtherInFlightRequest(TerminationRequest request) {
        boolean anotherInFlight = requestRepository.findByMemberId(request.getMemberId()).stream()
                .filter(other -> !other.getRequestNo().equals(request.getRequestNo()))
                .anyMatch(other -> IN_FLIGHT_STATUSES.contains(other.getStatus()));

        if (anotherInFlight) {
            throw new RuntimeException(
                    "Member " + request.getMemberId()
                            + " already has a termination request in progress."
            );
        }
    }

    /**
     * Decides which District Office locations the caller may actually see
     * (SRS MMT02).
     *
     * A DISTRICT_OFFICE user is pinned to their own assigned district and the
     * requested locations are ignored - the filter on the screen is a
     * convenience, not the boundary. Head Office, Board Secretary and Super
     * Admin may ask for any set, or for all of them.
     *
     * Returns null to mean "no location restriction". Note the difference that
     * makes for rows created before this column existed: an unrestricted caller
     * still sees them, while a district-scoped caller does not, because a null
     * location cannot be proven to belong to their district.
     */
    private Set<String> resolveVisibleLocations(List<String> requestedLocations) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User user
                && user.getRole() == Role.DISTRICT_OFFICE) {
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

    /**
     * The roles the SRS names as actors for MMT01-MMT04 - raising and working a
     * termination request, supporting documents included.
     */
    private void assertCallerIsRequestEntryRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return;
        }

        Role role = user.getRole();

        if (role != Role.DISTRICT_OFFICE && role != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Termination supporting documents are maintained by the District Office."
            );
        }
    }

    /**
     * The District Office a DISTRICT_OFFICE caller is pinned to, or null for any
     * role that is not district-scoped (Head Office, Board Secretary, Super Admin)
     * and for unauthenticated service-level callers.
     */
    private String callerAssignedDistrict() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof User user
                && user.getRole() == Role.DISTRICT_OFFICE) {
            return user.getAssignedDistrict();
        }

        return null;
    }

    /**
     * Server-side ownership check for every per-request operation (MMT02).
     *
     * searchRequests() has always scoped the LIST to the caller's own district, but
     * nothing scoped the operations reached by request number, so a District Office
     * user could edit, submit, mark incomplete or re-status another district's
     * request simply by knowing its id. The screen never offers those requests -
     * which is exactly why the check has to live here rather than in the UI.
     *
     * Requests with no submission location are refused for district callers, the
     * same way resolveVisibleLocations() hides them from the list: a null location
     * cannot be proven to belong to the caller's district. Rows created before that
     * column existed therefore need backfilling before their district can work them.
     */
    private void assertCallerMayAccess(TerminationRequest request) {
        String assignedDistrict = callerAssignedDistrict();

        if (assignedDistrict == null) {
            return;
        }

        if (assignedDistrict.isBlank() || !assignedDistrict.equals(request.getSubmissionLocation())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Termination request " + request.getRequestNo()
                            + " belongs to another District Office."
            );
        }
    }

    /** The same check, for a member who does not have a request yet (MMT01 create). */
    private void assertCallerMayActFor(Member member) {
        String assignedDistrict = callerAssignedDistrict();

        if (assignedDistrict == null) {
            return;
        }

        String memberLocation = resolveSubmissionLocationFor(member);

        if (assignedDistrict.isBlank() || !assignedDistrict.equals(memberLocation)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Member " + member.getMemberId() + " is administered by another District Office."
            );
        }
    }

    /**
     * The office to stamp on a new request.
     *
     * Prefers the member's own administering office, and falls back to the district
     * of the user raising it. Without that fallback every request created against a
     * member whose submissionLocation was never populated is stamped null, and the
     * district that just raised it cannot see it in their own list.
     */
    private String resolveSubmissionLocationFor(Member member) {
        String memberLocation = member.getSubmissionLocation();

        if (memberLocation != null && !memberLocation.isBlank()) {
            return memberLocation;
        }

        return callerAssignedDistrict();
    }

    /**
     * "Inactive rights" as the SRS uses the term, resolved the same way
     * MemberApplicationService does so one role list governs both modules.
     */
    private boolean currentUserHasInactiveRights() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return false;
        }

        Role role = user.getRole();
        return role == Role.HEAD_OFFICE || role == Role.BOARD_SECRETARY || role == Role.SUPER_ADMIN;
    }

    /**
     * A request may only be approved or rejected once the board has actually seen
     * it, which the SRS status flow defines as being on a Termination Approval
     * List (New -> Submitted for Approval -> Added to Termination Approval List
     * -> Approved / Rejected).
     *
     * Without this guard a request could be approved straight out of NEW,
     * bypassing the loan checks, the mandatory documents and the board entirely.
     */
    private void assertAwaitingBoardDecision(TerminationRequest request) {
        if (request.getStatus() != TerminationRequestStatus.ADDED_TO_APPROVAL_LIST) {
            throw new RuntimeException(
                    "Termination request " + request.getRequestNo()
                            + " cannot be approved or rejected from status " + request.getStatus()
                            + ". It must be added to a Termination Approval List first."
            );
        }
    }

    public TerminationRequestResponseDTO mapRequestToResponse(TerminationRequest request) {
        return mapToResponse(request);
    }

    public List<TerminationRequestResponseDTO> searchRequests(
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder,
            List<String> locations
    ) {
        Set<String> effectiveLocations = resolveVisibleLocations(locations);

        // Status and date filtering needs no extra queries, so it runs first and
        // shrinks the set the member lookup below has to cover.
        List<TerminationRequest> candidates = requestRepository.findAll()
                .stream()
                .filter(r -> effectiveLocations == null
                        || (r.getSubmissionLocation() != null
                            && effectiveLocations.contains(r.getSubmissionLocation())))
                .filter(r -> statuses == null || statuses.isEmpty()
                        || statuses.contains(r.getStatus().name()))
                .filter(r -> {
                    if (r.getRequestedDate() == null) return false;

                    LocalDate date = r.getRequestedDate();

                    if (fromDate != null && !fromDate.isBlank()) {
                        LocalDate from = LocalDate.parse(fromDate);
                        if (date.isBefore(from)) return false;
                    }

                    if (toDate != null && !toDate.isBlank()) {
                        LocalDate to = LocalDate.parse(toDate);
                        if (date.isAfter(to)) return false;
                    }

                    return true;
                })
                .toList();

        // One query for every member on the page. The map is shared by the search
        // filter and by the response mapping, replacing a lookup per row.
        Map<String, Member> membersById = loadMembersByMemberId(
                candidates.stream().map(TerminationRequest::getMemberId).toList()
        );

        List<TerminationRequest> matches = candidates.stream()
                .filter(r -> {
                    if (searchKey == null || searchKey.isBlank()) return true;

                    String key = searchKey.toLowerCase();
                    Member member = membersById.get(r.getMemberId());

                    return contains(r.getMemberId(), key)
                            || contains(r.getRequestNo(), key)
                            || (member != null && contains(member.getFullName(), key))
                            || (member != null && contains(member.getNameAsInPayroll(), key))
                            || (member != null && contains(member.getNameWithInitials(), key))
                            || (member != null && contains(member.getNic(), key));
                })
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
     * Batch counterpart of mapToResponse: resolves the loan flags and minor
     * disbursements for a whole page in three queries instead of three per row.
     * The DTOs it produces are identical to mapping each request individually.
     */
    private List<TerminationRequestResponseDTO> mapToResponses(
            List<TerminationRequest> requests,
            Map<String, Member> membersById
    ) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<String> memberIds = requests.stream()
                .map(TerminationRequest::getMemberId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> requestIds = requests.stream()
                .map(TerminationRequest::getId)
                .filter(Objects::nonNull)
                .toList();

        Set<String> membersWithLoanBalance = memberIds.isEmpty()
                ? Set.of()
                : new HashSet<>(loanRepository.findMemberIdsWithPositiveBalance(memberIds));
        Set<String> membersWithObligations = memberIds.isEmpty()
                ? Set.of()
                : new HashSet<>(obligationRepository.findMemberIdsWithObligations(memberIds));

        // Sorting by id before grouping keeps each request's disbursements in the
        // same order the lazy collection would have produced them in.
        Map<Long, List<TerminationMinorDisbursement>> disbursementsByRequestId = requestIds.isEmpty()
                ? Map.of()
                : minorDisbursementRepository.findByTerminationRequest_IdIn(requestIds)
                        .stream()
                        .sorted(Comparator.comparing(TerminationMinorDisbursement::getId))
                        .collect(Collectors.groupingBy(item -> item.getTerminationRequest().getId()));

        return requests.stream()
                .map(request -> buildResponse(
                        request,
                        membersById.get(request.getMemberId()),
                        membersWithLoanBalance.contains(request.getMemberId()),
                        membersWithObligations.contains(request.getMemberId()),
                        disbursementsByRequestId.getOrDefault(request.getId(), List.of())
                ))
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
                        (first, second) -> first
                ));
    }

    /**
     * Selectable options for the MMT01 "Termination due to" dropdown, straight
     * from the Termination Reasons Master. Only active reasons are offered, in
     * the master's own display order.
     */
    public List<TerminationReasonDTO> getTerminationReasonOptions() {
        return terminationReasonRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapReasonToDto)
                .toList();
    }

    /**
     * Resolves the submitted reason against the Termination Reasons Master and
     * copies the master's own name onto the request. The client-supplied
     * terminationReason text is deliberately ignored - only the master is
     * authoritative, exactly as bank and branch names are resolved from their id
     * in replaceMinorDisbursements().
     *
     * A deactivated reason can be kept but never newly chosen: it is accepted
     * only when the request already points at that same master row, so retiring
     * a reason cannot block or silently rewrite a request that is already using
     * it. Requests created before this master exists have no reference yet, so
     * for them any inactive selection is refused rather than guessed at.
     */
    private void applyTerminationReason(TerminationRequest request, MemberTerminationRequestDTO dto) {
        TerminationReason reason = terminationReasonRepository
                .findById(parseTerminationReasonId(dto.getTerminationReasonId()))
                .orElseThrow(() -> new RuntimeException("Invalid termination reason"));

        if (!reason.isActive() && !isCurrentTerminationReason(request, reason)) {
            throw new RuntimeException(
                    "Termination reason is no longer available: " + reason.getName()
            );
        }

        request.setTerminationReasonRef(reason);

        // The Phase 1 columns are kept in step with the master so that the
        // reason stays readable from the legacy fields alone - every screen and
        // the search filter still read them.
        request.setTerminationReasonId(String.valueOf(reason.getId()));
        request.setTerminationReason(reason.getName());
    }

    private boolean isCurrentTerminationReason(TerminationRequest request, TerminationReason reason) {
        TerminationReason current = request.getTerminationReasonRef();
        return current != null && reason.getId().equals(current.getId());
    }

    private Long parseTerminationReasonId(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new RuntimeException("Invalid termination reason");
        }
    }

    private TerminationReasonDTO mapReasonToDto(TerminationReason reason) {
        TerminationReasonDTO dto = new TerminationReasonDTO();
        dto.setId(reason.getId());
        dto.setCode(reason.getCode());
        dto.setName(reason.getName());
        return dto;
    }

    private void validateRequestDto(MemberTerminationRequestDTO dto) {
        // Only the id is checked here: the reason text is no longer supplied by
        // the client, it is resolved from the master in applyTerminationReason().
        if (dto.getTerminationReasonId() == null || dto.getTerminationReasonId().isBlank()) {
            throw new RuntimeException("Termination reason is required");
        }

        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            throw new RuntimeException("Requested Date is required");
        }

        if (dto.getEffectiveDate() == null || dto.getEffectiveDate().isBlank()) {
            throw new RuntimeException("Effective Date is required");
        }
    }

    private String generateRequestNo() {
        int year = LocalDate.now().getYear();
        String prefix = "T-" + year + "-";

        return requestRepository
                .findLastRequestByPrefix(prefix)
                .map(lastRequest -> {
                    String lastNo = lastRequest.getRequestNo();
                    int lastSeq = Integer.parseInt(
                            lastNo.substring(lastNo.lastIndexOf("-") + 1)
                    );
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    /**
     * Assembles the payload handed to the Finance Module on board approval
     * (MMT11).
     *
     * Built here rather than in FinanceTerminationClient so that the entity and
     * its child rows are mapped by the same code that maps every other
     * termination response - the client stays a thin HTTP shim with no knowledge
     * of the domain model.
     */
    @Transactional(readOnly = true)
    public FinanceTerminationHandoffDTO buildFinanceHandoff(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found: " + requestNo));

        Member member = memberRepository.findByMemberId(request.getMemberId()).orElse(null);

        FinanceTerminationHandoffDTO handoff = new FinanceTerminationHandoffDTO();
        handoff.setRequestNo(request.getRequestNo());
        handoff.setMemberId(request.getMemberId());
        handoff.setMemberName(member != null ? member.getNameWithInitials() : null);
        handoff.setNic(member != null ? member.getNic() : null);
        handoff.setEffectiveDate(request.getEffectiveDate());
        handoff.setTerminationReason(request.getTerminationReason());
        handoff.setMinorDisbursements(
                request.getMinorDisbursements().stream()
                        .map(this::mapMinorDisbursementToDto)
                        .toList()
        );

        return handoff;
    }

    private TerminationRequestResponseDTO mapToResponse(TerminationRequest request) {
        Member member = memberRepository.findByMemberId(request.getMemberId()).orElse(null);

        boolean hasLoanBalance = loanRepository
                .existsByMemberIdAndBalanceGreaterThan(request.getMemberId(), 0.0);

        boolean hasIndirectObligations = obligationRepository
                .existsByMemberId(request.getMemberId());

        return buildResponse(
                request,
                member,
                hasLoanBalance,
                hasIndirectObligations,
                request.getMinorDisbursements()
        );
    }

    private TerminationRequestResponseDTO buildResponse(
            TerminationRequest request,
            Member member,
            boolean hasLoanBalance,
            boolean hasIndirectObligations,
            List<TerminationMinorDisbursement> minorDisbursements
    ) {
        return new TerminationRequestResponseDTO(
                request.getId(),
                request.getRequestNo(),
                request.getMemberId(),
                member != null ? member.getFullName() : null,
                member != null ? member.getNameAsInPayroll() : null,
                member != null ? member.getNameWithInitials() : null,
                member != null ? member.getNic() : null,
                request.getTerminationReasonId(),
                request.getTerminationReason(),
                request.getRequestedDate() != null ? request.getRequestedDate().toString() : null,
                request.getEffectiveDate() != null ? request.getEffectiveDate().toString() : null,
                request.getComment(),
                request.getStatus().name(),
                request.getIncompleteReason(),
                request.getRejectReason(),
                hasLoanBalance,
                hasIndirectObligations,
                minorDisbursements.stream()
                        .map(this::mapMinorDisbursementToDto)
                        .toList()
        );
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    /**
     * Clears and rebuilds the minor-savings disbursement child rows for a termination
     * request, mirroring MemberDeathRecordService.replaceMinorAccounts().
     *
     * Every submitted minorAccountNo is verified against the member's own
     * MinorSavingsAccount records - unknown account numbers (including account numbers
     * that belong to a different member) are rejected, and duplicate rows for the same
     * minor account within the same submission are rejected too. Bank/branch names are
     * always resolved from the ID on the server; a client-supplied name is never trusted.
     */
    private void replaceMinorDisbursements(
            TerminationRequest request,
            String memberId,
            List<TerminationMinorDisbursementDTO> items
    ) {
        request.getMinorDisbursements().clear();

        if (items == null || items.isEmpty()) {
            return;
        }

        List<MinorSavingsAccount> minorAccounts = minorSavingsAccountRepository.findByMemberId(memberId);
        Set<String> seenAccountNos = new HashSet<>();

        for (TerminationMinorDisbursementDTO item : items) {
            if (item == null || item.getMinorAccountNo() == null || item.getMinorAccountNo().isBlank()) {
                continue;
            }

            String minorAccountNo = item.getMinorAccountNo().trim();

            MinorSavingsAccount matchedAccount = minorAccounts.stream()
                    .filter(account -> account.getMinorAccountNo().equals(minorAccountNo))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "Invalid minor savings account number for this member: " + minorAccountNo
                    ));

            if (!seenAccountNos.add(minorAccountNo)) {
                throw new RuntimeException(
                        "Duplicate disbursement entry for minor savings account: " + minorAccountNo
                );
            }

            TerminationMinorDisbursement disbursement = new TerminationMinorDisbursement();
            disbursement.setTerminationRequest(request);
            disbursement.setMinorAccountNo(minorAccountNo);
            disbursement.setMinorAccountHolderName(matchedAccount.getHolderName());
            disbursement.setDisbursementBank(resolveBankName(item.getDisbursementBankId()));
            disbursement.setDisbursementBankId(item.getDisbursementBankId());
            disbursement.setBranch(resolveBranchName(item.getDisbursementBranchId()));
            disbursement.setDisbursementBranchId(item.getDisbursementBranchId());
            disbursement.setDisbursementAccountNumber(trimToNull(item.getDisbursementAccountNo()));

            request.getMinorDisbursements().add(disbursement);
        }
    }

    /**
     * Server-side gate for Submit: mirrors MemberDeathRecordService.validateMinorAccountsForSubmit().
     * Every minor savings account the member currently has must have a matching
     * disbursement row with bank, branch and account number all present.
     */
    private void validateMinorDisbursementsForSubmit(TerminationRequest request) {
        List<MinorSavingsAccount> minorAccounts =
                minorSavingsAccountRepository.findByMemberId(request.getMemberId());

        if (minorAccounts.isEmpty()) {
            return;
        }

        for (MinorSavingsAccount account : minorAccounts) {
            TerminationMinorDisbursement disbursement = request.getMinorDisbursements().stream()
                    .filter(item -> account.getMinorAccountNo().equals(item.getMinorAccountNo()))
                    .findFirst()
                    .orElse(null);

            if (disbursement == null
                    || disbursement.getDisbursementBank() == null || disbursement.getDisbursementBank().isBlank()
                    || disbursement.getBranch() == null || disbursement.getBranch().isBlank()
                    || disbursement.getDisbursementAccountNumber() == null
                    || disbursement.getDisbursementAccountNumber().isBlank()) {
                throw new RuntimeException(
                        "Disbursement details are required for minor savings account: "
                                + account.getMinorAccountNo()
                );
            }
        }
    }

    private TerminationMinorDisbursementDTO mapMinorDisbursementToDto(TerminationMinorDisbursement item) {
        TerminationMinorDisbursementDTO dto = new TerminationMinorDisbursementDTO();
        dto.setId(item.getId());
        dto.setMinorAccountNo(item.getMinorAccountNo());
        dto.setHolderName(item.getMinorAccountHolderName());
        dto.setDisbursementBankName(item.getDisbursementBank());
        dto.setDisbursementBankId(item.getDisbursementBankId());
        dto.setDisbursementBranchName(item.getBranch());
        dto.setDisbursementBranchId(item.getDisbursementBranchId());
        dto.setDisbursementAccountNo(item.getDisbursementAccountNumber());
        return dto;
    }

    // Returns null (not a placeholder like "-") when the bank id is missing or unknown,
    // so that submit-time completeness checks correctly treat it as not provided.
    private String resolveBankName(Long bankId) {
        if (bankId == null) {
            return null;
        }
        return bankRepository.findById(bankId)
                .map(bank -> bank.getName())
                .orElse(null);
    }

    private String resolveBranchName(Long branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findById(branchId)
                .map(branch -> branch.getName())
                .orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
