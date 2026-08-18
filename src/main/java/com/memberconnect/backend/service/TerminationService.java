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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberTerminationRequestDTO;
import com.memberconnect.backend.dto.TerminationMinorDisbursementDTO;
import com.memberconnect.backend.dto.TerminationReasonDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.event.TerminationMarkedIncompleteEvent;
import com.memberconnect.backend.model.Loan;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.model.TerminationMinorDisbursement;
import com.memberconnect.backend.model.TerminationReason;
import com.memberconnect.backend.model.TerminationRequest;
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
            ApplicationEventPublisher eventPublisher
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

        if (DOCUMENT_LOCK_STATUSES.contains(request.getStatus())) {
            throw new RuntimeException(
                    "Cannot modify documents: termination request is already "
                            + request.getStatus().name()
            );
        }
    }

    public List<TerminationRequestResponseDTO> getRequestsByMember(String memberId) {
        return requestRepository.findByMemberId(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public TerminationRequestResponseDTO saveRequest(String memberId, MemberTerminationRequestDTO dto) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

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

        if (existingRequest != null) {
            request = existingRequest;

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
        }

        applyTerminationReason(request, dto);
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        replaceMinorDisbursements(request, memberId, dto.getMinorDisbursements());

        TerminationRequest saved = requestRepository.save(request);

        member.setStatus(MemberStatus.TERMINATION_REQUESTED);
        memberRepository.save(member);

        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO updateRequest(
            String requestNo,
            MemberTerminationRequestDTO dto
    ) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

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
        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO submitRequest(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

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

        request.setStatus(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
        TerminationRequest saved = requestRepository.save(request);

        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO markIncomplete(String requestNo, String reason) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        request.setStatus(TerminationRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason);

        TerminationRequest saved = requestRepository.save(request);

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

    public TerminationRequestResponseDTO approveRequest(String requestNo) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        request.setStatus(TerminationRequestStatus.APPROVED);
        TerminationRequest saved = requestRepository.save(request);

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));
        member.setStatus(MemberStatus.TERMINATED);
        memberRepository.save(member);

        return mapToResponse(saved);
    }

    public TerminationRequestResponseDTO rejectRequest(String requestNo, String reason) {
        TerminationRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Termination request not found"));

        request.setStatus(TerminationRequestStatus.REJECTED);
        request.setRejectReason(reason);

        TerminationRequest saved = requestRepository.save(request);

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);

        return mapToResponse(saved);
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
            String sortOrder
    ) {
        // Status and date filtering needs no extra queries, so it runs first and
        // shrinks the set the member lookup below has to cover.
        List<TerminationRequest> candidates = requestRepository.findAll()
                .stream()
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
            disbursement.setBranch(resolveBranchName(item.getDisbursementBranchId()));
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
        dto.setDisbursementBranchName(item.getBranch());
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
