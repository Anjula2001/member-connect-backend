package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.MemberRetirementRequestDTO;
import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberSummaryDTO;
import com.memberconnect.backend.dto.RetirementRequestResponseDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.RetirementRequestStatus;
import com.memberconnect.backend.model.Loan;
import com.memberconnect.backend.model.LoanObligation;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.RetirementRequest;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.RetirementRequestRepository;

@Service
public class RetirementService {

    private final MemberRepository memberRepository;
    private final RetirementRequestRepository requestRepository;
    private final LoanRepository loanRepository;
    private final LoanObligationRepository obligationRepository;
    private final DocumentService documentService;

    public RetirementService(
            MemberRepository memberRepository,
            RetirementRequestRepository requestRepository,
            LoanRepository loanRepository,
            LoanObligationRepository obligationRepository,
            DocumentService documentService
    ) {
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.loanRepository = loanRepository;
        this.obligationRepository = obligationRepository;
        this.documentService = documentService;
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

    // Validate member for retirement request
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

    //generate request number 
    private String generateRequestNo() {
        int year = LocalDate.now().getYear();
        String prefix = "R-" + year + "-";

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

   public RetirementRequestResponseDTO saveRequest(String memberId, MemberRetirementRequestDTO dto) {

        //Get member
        Member member = getMemberEntity(memberId);

        //Validate member status
        if (member.getStatus() != MemberStatus.ACTIVE &&
            member.getStatus() != MemberStatus.RETIREMENT_REQUESTED) {
            throw new RuntimeException("Only ACTIVE or RETIREMENT_REQUESTED member can save retirement request");
        }

        //Validate required fields
        if (dto.getRequestedDate() == null || dto.getRequestedDate().isBlank()) {
            throw new RuntimeException("Requested Date is required");
        }

        if (dto.getEffectiveDate() == null || dto.getEffectiveDate().isBlank()) {
            throw new RuntimeException("Effective Date is required");
        }

        //Parse dates
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

        //Check existing request (ONLY ONE ACTIVE REQUEST)
        RetirementRequest existingRequest = requestRepository
                .findByMemberId(memberId)
                .stream()
                .filter(r -> r.getStatus() != RetirementRequestStatus.INACTIVE)
                .findFirst()
                .orElse(null);

        RetirementRequest request;

        if (existingRequest != null) {

            //EDIT MODE
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

            //NEW MODE
            request = new RetirementRequest();
            request.setRequestNo(generateRequestNo());
            request.setMemberId(memberId);
            request.setStatus(RetirementRequestStatus.NEW);
        }

        //update values
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        //Save request
        RetirementRequest saved = requestRepository.save(request);

        //Update member status
        member.setStatus(MemberStatus.RETIREMENT_REQUESTED);
        memberRepository.save(member);

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
                        "RETIREMENT"
        );

        if (!allMandatoryUploaded) {
            throw new RuntimeException("Cannot submit. Mandatory documents are missing.");
        }

        request.setStatus(RetirementRequestStatus.SUBMITTED_FOR_APPROVAL);
        RetirementRequest saved = requestRepository.save(request);

        return mapToResponse(saved);
    }

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
                    "Cannot change status from " + currentStatus + " to " + newStatus
            );
        }

        request.setStatus(newStatus);

        if (newStatus == RetirementRequestStatus.INACTIVE) {
            request.setIncompleteReason(null);
            request.setRejectReason(null);

            Member member = getMemberEntity(request.getMemberId());
            member.setStatus(MemberStatus.ACTIVE);
            memberRepository.save(member);
        } else if (newStatus == RetirementRequestStatus.NEW ||
                newStatus == RetirementRequestStatus.INCOMPLETE ||
                newStatus == RetirementRequestStatus.SUBMITTED_FOR_APPROVAL) {
            request.setRejectReason(null);
            if (newStatus == RetirementRequestStatus.NEW) {
                request.setIncompleteReason(null);
            }

            Member member = getMemberEntity(request.getMemberId());
            member.setStatus(MemberStatus.RETIREMENT_REQUESTED);
            memberRepository.save(member);
        }

        RetirementRequest saved = requestRepository.save(request);
        return mapToResponse(saved);
    }

    private boolean isStatusTransitionAllowed(RetirementRequestStatus current, RetirementRequestStatus next) {
        switch (current) {
            case NEW:
                return next == RetirementRequestStatus.INACTIVE || next == RetirementRequestStatus.INCOMPLETE;
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

    // Marks a retirement request as incomplete
    public RetirementRequestResponseDTO markIncomplete(String requestNo, String reason) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason);

        RetirementRequest saved = requestRepository.save(request);
        return mapToResponse(saved);
    }

    // Approve retirement request
    public RetirementRequestResponseDTO approveRequest(String requestNo) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.APPROVED);
        RetirementRequest saved = requestRepository.save(request);

        Member member = getMemberEntity(request.getMemberId());
        member.setStatus(MemberStatus.RETIREMENT_APPROVED);
        memberRepository.save(member);

        return mapToResponse(saved);
    }

    //reject retirement request
    public RetirementRequestResponseDTO rejectRequest(String requestNo, String reason) {
        RetirementRequest request = requestRepository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.REJECTED);
        request.setRejectReason(reason);

        RetirementRequest saved = requestRepository.save(request);

        Member member = getMemberEntity(request.getMemberId());
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);

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

        return new RetirementRequestResponseDTO(
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
                hasIndirectObligations
        );
    }

    // Update request details
    public RetirementRequestResponseDTO updateRequest(
            String requestNo,
            MemberRetirementRequestDTO dto
    ) {
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

    //filtering rquests
    public List<RetirementRequestResponseDTO> searchRequests(
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder
    ) {
        return requestRepository.findAll()
                .stream()

                //status filter
                .filter(r -> statuses == null || statuses.isEmpty()
                        || statuses.contains(r.getStatus().name()))
                
                //date filter
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

                //search filter
                .filter(r -> {
                    if (searchKey == null || searchKey.isBlank()) return true;

                    String key = searchKey.toLowerCase();

                    Member member = memberRepository.findByMemberId(r.getMemberId())
                            .orElse(null);

                    return contains(r.getMemberId(), key)
                            || (member != null && contains(member.getFullName(), key))
                            || (member != null && contains(member.getNameAsInPayroll(), key))
                            || (member != null && contains(member.getNameWithInitials(), key))
                            || (member != null && contains(member.getNic(), key));
                })

                //sorting
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
                .map(this::mapToResponse)
                .toList();
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }
}
