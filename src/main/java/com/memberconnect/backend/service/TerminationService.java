package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.MemberRetirementValidationDTO;
import com.memberconnect.backend.dto.MemberTerminationRequestDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

@Service
public class TerminationService {

    private final MemberRepository memberRepository;
    private final TerminationRequestRepository requestRepository;
    private final LoanRepository loanRepository;
    private final LoanObligationRepository obligationRepository;
    private final DocumentService documentService;
    private final RetirementService retirementService;

    public TerminationService(
            MemberRepository memberRepository,
            TerminationRequestRepository requestRepository,
            LoanRepository loanRepository,
            LoanObligationRepository obligationRepository,
            DocumentService documentService,
            RetirementService retirementService
    ) {
        this.memberRepository = memberRepository;
        this.requestRepository = requestRepository;
        this.loanRepository = loanRepository;
        this.obligationRepository = obligationRepository;
        this.documentService = documentService;
        this.retirementService = retirementService;
    }

    public MemberRetirementValidationDTO validateMemberForTermination(String memberId) {
        return retirementService.validateMemberForRetirement(memberId);
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

        request.setTerminationReasonId(dto.getTerminationReasonId());
        request.setTerminationReason(dto.getTerminationReason());
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

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

        request.setTerminationReasonId(dto.getTerminationReasonId());
        request.setTerminationReason(dto.getTerminationReason());
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        request.setComment(dto.getComment());

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
        return mapToResponse(saved);
    }

    public List<TerminationRequestResponseDTO> searchRequests(
            List<String> statuses,
            String fromDate,
            String toDate,
            String searchKey,
            String sortBy,
            String sortOrder
    ) {
        return requestRepository.findAll()
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
                .filter(r -> {
                    if (searchKey == null || searchKey.isBlank()) return true;

                    String key = searchKey.toLowerCase();
                    Member member = memberRepository.findByMemberId(r.getMemberId()).orElse(null);

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
                .map(this::mapToResponse)
                .toList();
    }

    private void validateRequestDto(MemberTerminationRequestDTO dto) {
        if (dto.getTerminationReasonId() == null || dto.getTerminationReasonId().isBlank()) {
            throw new RuntimeException("Termination reason is required");
        }

        if (dto.getTerminationReason() == null || dto.getTerminationReason().isBlank()) {
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
                hasIndirectObligations
        );
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }
}
