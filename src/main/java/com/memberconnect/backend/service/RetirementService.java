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

            //NEW MODE
            request = new RetirementRequest();
            request.setRequestNo(generateRequestNo());
            request.setMemberId(memberId);
            request.setStatus(RetirementRequestStatus.NEW);
        }

        //Set / update values
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

        //Save request
        RetirementRequest saved = requestRepository.save(request);

        //Update member status
        member.setStatus(MemberStatus.RETIREMENT_REQUESTED);
        memberRepository.save(member);

        //Return response
        return mapToResponse(saved);
    }

    public RetirementRequestResponseDTO submitRequest(Long requestId) {
        RetirementRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        MemberRetirementValidationDTO validation = validateMemberForRetirement(request.getMemberId());

        if (!validation.isCanSubmit()) {
            throw new RuntimeException("Cannot submit: " + validation.getMessage());
        }

        boolean allMandatoryUploaded =
                documentService.allMandatoryDocumentsUploaded(
                        request.getId(),
                        request.getMemberId()
                );

        if (!allMandatoryUploaded) {
            throw new RuntimeException("Cannot submit. Mandatory documents are missing.");
        }

        request.setStatus(RetirementRequestStatus.SUBMITTED_FOR_APPROVAL);
        RetirementRequest saved = requestRepository.save(request);

        return mapToResponse(saved);
    }

    public RetirementRequestResponseDTO markIncomplete(Long requestId, String reason) {
        RetirementRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.INCOMPLETE);
        request.setIncompleteReason(reason);

        RetirementRequest saved = requestRepository.save(request);
        return mapToResponse(saved);
    }

    public RetirementRequestResponseDTO approveRequest(Long requestId) {
        RetirementRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.APPROVED);
        RetirementRequest saved = requestRepository.save(request);

        Member member = getMemberEntity(request.getMemberId());
        member.setStatus(MemberStatus.RETIREMENT_APPROVED);
        memberRepository.save(member);

        return mapToResponse(saved);
    }

    public RetirementRequestResponseDTO rejectRequest(Long requestId, String reason) {
        RetirementRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Retirement request not found"));

        request.setStatus(RetirementRequestStatus.REJECTED);
        request.setRejectReason(reason);

        RetirementRequest saved = requestRepository.save(request);

        Member member = getMemberEntity(request.getMemberId());
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);

        return mapToResponse(saved);
    }

    public List<RetirementRequestResponseDTO> getRequestsByMember(String memberId) {
        return requestRepository.findByMemberId(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

   private RetirementRequestResponseDTO mapToResponse(RetirementRequest request) {
    return new RetirementRequestResponseDTO(
            request.getId(),
            request.getRequestNo(),
            request.getMemberId(),
            request.getRequestedDate() != null ? request.getRequestedDate().toString() : null,
            request.getEffectiveDate() != null ? request.getEffectiveDate().toString() : null,
            request.getComment(),
            request.getStatus().name(),
            request.getIncompleteReason(),
            request.getRejectReason()
    );
}
}