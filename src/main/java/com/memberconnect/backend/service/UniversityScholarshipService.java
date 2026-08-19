package com.memberconnect.backend.service;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.dto.UniversityScholarshipFundRequestDto;
import com.memberconnect.backend.dto.UniversityScholarshipRequestDto;
import com.memberconnect.backend.enums.ApplicantType;
import com.memberconnect.backend.enums.UniversityScholarshipFundRequestStatus;
import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;
import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.model.MinorAccount;
import com.memberconnect.backend.model.Program;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityProgram;
import com.memberconnect.backend.model.UniversityScholarshipFundRequest;
import com.memberconnect.backend.model.UniversityScholarshipExamMaster;
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.MinorAccountRepository;
import com.memberconnect.backend.repository.ProgramRepository;
import com.memberconnect.backend.repository.ScholarshipMonthSettlementRepository;
import com.memberconnect.backend.repository.UniversityProgramRepository;
import com.memberconnect.backend.repository.UniversityRepository;
import com.memberconnect.backend.repository.UniversityScholarshipExamMasterRepository;
import com.memberconnect.backend.repository.UniversityScholarshipFundRequestRepository;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.ScholarshipRemittanceRepository;
import org.springframework.beans.factory.annotation.Value;
import com.memberconnect.backend.dto.UniversityScholarshipListDto;
import org.springframework.util.StringUtils;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UniversityScholarshipService {

    private final UniversityRepository universityRepository;
    private final ProgramRepository programRepository;
    private final UniversityProgramRepository universityProgramRepository;
    private final UniversityScholarshipRequestRepository scholarshipRequestRepository;
    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;
    private final MinorAccountRepository minorAccountRepository;
    private final MemberRepository memberRepository;
    private final UniversityScholarshipExamMasterRepository examMasterRepository;
        private final com.memberconnect.backend.repository.ScholarshipConfigRepository scholarshipConfigRepository;
    private final int minimumMembershipYears = 5;
    private final ScholarshipRemittanceRepository remittanceRepository;
    private final ScholarshipMonthSettlementRepository settlementRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final UniversityScholarshipFundRequestRepository fundRequestRepository;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private com.memberconnect.backend.config.CurrentUserService currentUserService;

    @Value("${scholarship.required.remitted.months}")
    private int requiredRemittedMonths;

    @Value("${scholarship.lookback.years}")
    private int lookbackYears;

    @Value("${university.scholarship.minor.required.amount:500}")
    private int minorRequiredAmount;

    @Value("${university.scholarship.minor.required.months:120}")
    private int minorRequiredMonths;

    @Value("${university.scholarship.minor.multiplier.percent:150}")
    private int minorMultiplierPercent;

    public UniversityScholarshipService(
            UniversityRepository universityRepository,
            ProgramRepository programRepository,
            UniversityProgramRepository universityProgramRepository,
            UniversityScholarshipRequestRepository scholarshipRequestRepository,
            BankRepository bankRepository,
            BranchRepository branchRepository,
            MinorAccountRepository minorAccountRepository,
            MemberRepository memberRepository,
            UniversityScholarshipExamMasterRepository examMasterRepository,
            com.memberconnect.backend.repository.ScholarshipConfigRepository scholarshipConfigRepository,
            ScholarshipRemittanceRepository remittanceRepository,
            ScholarshipMonthSettlementRepository settlementRepository,
            BoardmeetingRepository boardMeetingRepository,
            UniversityScholarshipFundRequestRepository fundRequestRepository
    ) {
        this.universityRepository = universityRepository;
        this.programRepository = programRepository;
        this.universityProgramRepository = universityProgramRepository;
        this.scholarshipRequestRepository = scholarshipRequestRepository;
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
        this.minorAccountRepository = minorAccountRepository;
        this.memberRepository = memberRepository;
        this.examMasterRepository = examMasterRepository;
        this.scholarshipConfigRepository = scholarshipConfigRepository;
        this.remittanceRepository = remittanceRepository;
        this.settlementRepository = settlementRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.fundRequestRepository = fundRequestRepository;
    }
   
    // Validate member active status 
    private void validateMemberActiveOnExamLastDate(UniversityScholarshipRequestDto dto) {
        
        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (member.getStatus() == null || !"ACTIVE".equalsIgnoreCase(member.getStatus().name())) {
            throw new RuntimeException(
                    "The University Scholarship Request cannot be saved. The Member is not Active "
            );
        }
    }
    
    // Validate membership duration of the member at the time of exam last date
    private void validateMembershipDuration(UniversityScholarshipRequestDto dto) {

        // Get exam last date
        UniversityScholarshipExamMaster examMaster = examMasterRepository
                .findByExamYear(dto.getExamYear())
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamLastDate();

        // Get member
        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        LocalDate membershipStartDate = member.getMembershipStartDate();

        if (membershipStartDate == null) {
            throw new RuntimeException("Membership start date not found");
        }

        // Calculate years
        long years = ChronoUnit.YEARS.between(membershipStartDate, examLastDate);

        if (years < minimumMembershipYears) {
            throw new RuntimeException(
                    "Required continues Membership period does not comply (" + minimumMembershipYears + " years)"
            );
        }
    }

     // Validate remittance months for the scholarship request
     private void validateScholarshipRemittanceMonths(UniversityScholarshipRequestDto dto) {

        UniversityScholarshipExamMaster examMaster = examMasterRepository
                .findByExamYear(dto.getExamYear())
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamLastDate();

        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        String endMonth = examLastDate.withDayOfMonth(1).toString().substring(0, 7);
        String startMonth = examLastDate
                .minusYears(lookbackYears)
                .withDayOfMonth(1)
                .toString()
                .substring(0, 7);

        long remittedMonthCount =
                remittanceRepository.countByMember_IdAndRemittedTrueAndRemittanceMonthBetween(
                        member.getId(),
                        startMonth,
                        endMonth
                );

        if (remittedMonthCount < requiredRemittedMonths) {
            throw new RuntimeException(
                    "Scholarship amount was not continuously remitted from the Member for the specified period"
            );
        }
    }
 
    // Validate that scholarship amounts were settled for the months where remittance was done
    private void validateRemainingMonthsSettled(UniversityScholarshipRequestDto dto) {

        UniversityScholarshipExamMaster examMaster = examMasterRepository
                .findByExamYear(dto.getExamYear())
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamLastDate();

        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        LocalDate startDate = examLastDate
                .minusYears(lookbackYears)
                .withDayOfMonth(1);

        LocalDate endDate = examLastDate.withDayOfMonth(1);

        LocalDate currentMonth = startDate;

        while (!currentMonth.isAfter(endDate)) {
            String month = currentMonth.toString().substring(0, 7);

            boolean remitted = remittanceRepository
                    .existsByMember_IdAndRemittanceMonthAndRemittedTrue(
                            member.getId(),
                            month
                    );

            boolean settled = settlementRepository
                    .existsByMember_IdAndSettlementMonthAndSettledTrue(
                            member.getId(),
                            month
                    );

            if (!remitted && !settled) {
                throw new RuntimeException(
                        "The Scholarship Amounts were not settled for some months"
                );
            }

            currentMonth = currentMonth.plusMonths(1);
        }
    }

    // Validate that no other scholarship was approved for the member within a year from the exam last date
    private void validateAnotherApprovedScholarshipWithinYear(UniversityScholarshipRequestDto dto) {

        UniversityScholarshipExamMaster examMaster = examMasterRepository
                .findByExamYear(dto.getExamYear())
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamLastDate();

        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        LocalDate startDate = examLastDate.minusYears(1);
        LocalDate endDate = examLastDate;

        boolean exists = scholarshipRequestRepository
            .existsByMember_MemberIdAndStatusAndAcademicYearStartDateBetween(
                    member.getMemberId(),
                    UniversityScholarshipRequestStatus.APPROVED,
                    startDate,
                    endDate
            );

        if (exists) {
            throw new RuntimeException(
                    "Another University Scholarship was approved for the Member within a year"
            );
        }
    }

    // Get all scholarship requests with member and university details
    /**
     * Every University Scholarship the caller is allowed to see.
     *
     * The MMS22 filters (status, date period, search key, sort) are still applied in
     * the browser, but Location is enforced here. It has to be: a District Office user
     * must not receive another branch's records at all, and a filter the client applies
     * to data it already holds is a display preference, not a control.
     */
    public List<UniversityScholarshipListDto> getAllScholarshipRequests() {
        CurrentUserService.LocationScope locationScope =
                currentUserService.resolveLocationScope(null);

        // Restricted, but no district on the account: return nothing rather than
        // falling back to "everything".
        if (locationScope.showsNothing()) {
            return List.of();
        }

        return scholarshipRequestRepository.findAll()
                .stream()
                .filter(request -> currentUserService.matchesScope(
                        locationScope, request.getSubmissionLocation()))
                .map(this::toListDto)
                .toList();
    }

    // Get scholarship request by request ID with member and university details
    public UniversityScholarshipListDto getScholarshipRequestByRequestId(String requestId) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

        // Scoping the list but not the by-ID lookup would leave the whole dataset
        // reachable by iterating request IDs.
        CurrentUserService.LocationScope locationScope =
                currentUserService.resolveLocationScope(null);
        if (locationScope.showsNothing()
                || !currentUserService.matchesScope(locationScope, request.getSubmissionLocation())) {
            throw new AccessDeniedException("This scholarship request belongs to another District Office");
        }

        return toListDto(request);
    }

    private UniversityScholarshipListDto toListDto(UniversityScholarshipRequest request) {
        UniversityScholarshipListDto dto = new UniversityScholarshipListDto(
                request.getId(),
                request.getMember() != null ? request.getMember().getMemberId() : null,
                request.getUniversityScholarshipRequestID(),
                request.getStudentName(),
                request.getMember() != null ? request.getMember().getFullName() : "",
                request.getUniversity() != null ? request.getUniversity().getName() : "",
                request.getStatus() != null ? request.getStatus().name() : "",
                request.getNic() != null ? request.getNic() : "",
                request.getBcNo() != null ? request.getBcNo() : "",
                request.getAddress() != null ? request.getAddress() : "",
                request.getMobile() != null ? request.getMobile() : "",
                request.getApplicantType() != null ? request.getApplicantType().name() : "",
                request.getExamYear() != null ? request.getExamYear() : "",
                request.getExamNo() != null ? request.getExamNo() : "",
                request.getZScore() != null ? request.getZScore() : "",
                request.getProgram() != null ? request.getProgram().getName() : "",
                request.getDuration() != null ? request.getDuration() : "",
                request.getRequestDate(),
                request.getAcademicYearStart(),
                request.getHasMinorAccount() != null ? request.getHasMinorAccount().name() : "",
                request.getMinorAccountMonths() != null ? request.getMinorAccountMonths() : "",
                request.getBank() != null ? request.getBank().getName() : "",
                request.getBranch() != null ? request.getBranch().getName() : "",
                request.getAccountNo() != null ? request.getAccountNo() : "",
                request.getSpecialDegree(),
                request.getIncompleteReason() != null ? request.getIncompleteReason() : ""
        );
        if (request.getBoardMeeting() != null) {
            dto.setBoardMeetingId(request.getBoardMeeting().getId());
            dto.setBoardMeetingName(request.getBoardMeeting().getBoardMeetingId());
            dto.setScheduledDate(request.getBoardMeeting().getScheduledDate());
        }
        dto.setApprovalListId(request.getApprovalListId());
        dto.setProcessedBy(request.getProcessedBy());
        dto.setProcessedAt(request.getProcessedAt());
        dto.setRejectReason(request.getRejectReason());
        dto.setScannedReportPath(request.getScannedReportPath());
        dto.setFollowDeviationProcess(request.getFollowDeviationProcess());
        List<UniversityScholarshipFundRequest> fundRequests =
                fundRequestRepository.findByUniversityScholarshipRequest(request);
        dto.setTotalScholarshipAmount(getStoredTotalScholarshipAmount(request));
        dto.setTotalDisbursedAmount(calculateTotalDisbursedAmount(fundRequests));
        dto.setLastDisbursementDate(getLastDisbursementDate(fundRequests));
        dto.setAvailablePeriod(calculateAvailablePeriod(request, null));
        dto.setFundRequests(fundRequests.stream().map(this::toFundRequestDto).toList());
        dto.setTotalUniversityScholarships(countApprovedUniversityScholarships(request));
        // The District Office that owns the request. The MMS22 Location filter reads
        // this; it previously had nothing to read and fell back to the student's
        // free-text address, which could never match a district name.
        dto.setSubmissionLocation(request.getSubmissionLocation());
        return dto;
    }

    public List<UniversityScholarshipListDto> getScholarshipRequestsByMemberId(String memberId) {
        return scholarshipRequestRepository.findByMember_MemberIdAndStatus(
                        memberId,
                        UniversityScholarshipRequestStatus.APPROVED
                )
                .stream()
                .map(this::toListDto)
                .toList();
    }

    private int countApprovedUniversityScholarships(UniversityScholarshipRequest request) {
        if (request.getMember() == null || !StringUtils.hasText(request.getMember().getMemberId())) {
            return 0;
        }

        return (int) scholarshipRequestRepository.countByMember_MemberIdAndStatus(
                request.getMember().getMemberId(),
                UniversityScholarshipRequestStatus.APPROVED
        );
    }

    private Double getStoredTotalScholarshipAmount(UniversityScholarshipRequest request) {
        if (request.getTotalScholarshipAmount() != null && request.getTotalScholarshipAmount() > 0) {
            return request.getTotalScholarshipAmount();
        }

        Double calculatedAmount = calculateTotalScholarshipAmount(request);
        if (calculatedAmount != null && calculatedAmount > 0) {
            request.setTotalScholarshipAmount(calculatedAmount);
            scholarshipRequestRepository.save(request);
        }

        return calculatedAmount;
    }

    private Double calculateTotalScholarshipAmount(UniversityScholarshipRequest request) {
        if (request.getUniversity() == null || request.getProgram() == null) {
            return 0.0;
        }

        Optional<UniversityProgram> universityProgram = universityProgramRepository
                .findByUniversityIdAndProgramId(request.getUniversity().getId(), request.getProgram().getId());

        double baseAmount = universityProgram
                .map(UniversityProgram::getScholarshipAmount)
                .orElse(0.0);

        if (baseAmount <= 0) {
            return 0.0;
        }

        boolean hasMinorAccount = request.getHasMinorAccount() != null
                && "YES".equalsIgnoreCase(request.getHasMinorAccount().name());
        int remittedMonths = parseInt(request.getMinorAccountMonths());

        if (hasMinorAccount && remittedMonths >= minorRequiredMonths) {
            return baseAmount * minorMultiplierPercent / 100.0;
        }

        return baseAmount;
    }

    private void updateTotalScholarshipAmount(UniversityScholarshipRequest request) {
        request.setTotalScholarshipAmount(calculateTotalScholarshipAmount(request));
    }

    private int parseInt(String input) {
        if (input == null) {
            return 0;
        }
        try {
            return Integer.parseInt(input.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Double calculateTotalDisbursedAmount(List<UniversityScholarshipFundRequest> fundRequests) {
        return fundRequests.stream()
                .map(UniversityScholarshipFundRequest::getDisbursedAmount)
                .filter(amount -> amount != null)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private LocalDate getLastDisbursementDate(List<UniversityScholarshipFundRequest> fundRequests) {
        return fundRequests.stream()
                .map(UniversityScholarshipFundRequest::getDisbursementDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private Integer calculateAvailablePeriod(UniversityScholarshipRequest request, UniversityScholarshipFundRequest excludedRequest) {
        int years = parseInt(request.getDuration());
        if (years <= 0) {
            return null;
        }

        int totalPeriods = years * 2;
        long requestedPeriods = fundRequestRepository.findByUniversityScholarshipRequest(request)
                .stream()
                .filter(fundRequest -> excludedRequest == null
                        || fundRequest.getId() == null
                        || !fundRequest.getId().equals(excludedRequest.getId()))
                .filter(fundRequest -> fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.REJECTED
                        && fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.INACTIVE)
                .count();

        return Math.max(0, totalPeriods - (int) requestedPeriods);
    }

    private int parseRequestedPeriod(String requestedPeriod) {
        if (!StringUtils.hasText(requestedPeriod)) {
            return 0;
        }

        String trimmed = requestedPeriod.trim();

        java.util.regex.Matcher semMatcher = java.util.regex.Pattern
                .compile("(?i)^Semester\\s+([1-9][0-9]*)$")
                .matcher(trimmed);
        if (semMatcher.matches()) {
            return Integer.parseInt(semMatcher.group(1));
        }

        java.util.regex.Matcher yearSemMatcher = java.util.regex.Pattern
                .compile("(?i)^Year\\s+([1-5])\\s+Semester\\s+([1-2])$")
                .matcher(trimmed);
        if (yearSemMatcher.matches()) {
            int year = Integer.parseInt(yearSemMatcher.group(1));
            int semester = Integer.parseInt(yearSemMatcher.group(2));
            return (year - 1) * 2 + semester;
        }

        return 0;
    }
    
    // Save Fund Request 
    private UniversityScholarshipFundRequestDto toFundRequestDto(UniversityScholarshipFundRequest request) {
        UniversityScholarshipFundRequestDto dto = new UniversityScholarshipFundRequestDto();
        dto.setId(request.getId());
        dto.setRequestId(request.getFundRequestId());
        dto.setScholarshipRequestId(request.getUniversityScholarshipRequest() != null
                ? request.getUniversityScholarshipRequest().getUniversityScholarshipRequestID()
                : null);
        dto.setRequestedDate(request.getRequestedDate());
        dto.setRequestedPeriod(request.getRequestedPeriod());
        dto.setRequestedAmount(request.getRequestedAmount());
        dto.setDisbursedAmount(request.getDisbursedAmount());
        dto.setDisbursementDate(request.getDisbursementDate());
        dto.setStatus(request.getStatus() != null ? request.getStatus().name() : null);
        dto.setIncompleteReason(request.getIncompleteReason());
        dto.setDecisionReason(request.getDecisionReason());
        return dto;
    }
    
    //Generate Fund Request ID
    private String generateFundRequestId() {
        long nextNumber = fundRequestRepository.count() + 1;
        return String.format("USFR-%03d", nextNumber);
    }

    public UniversityScholarshipFundRequestDto saveFundRequest(
            String scholarshipRequestId,
            UniversityScholarshipFundRequestDto dto
    ) {
        UniversityScholarshipRequest scholarshipRequest = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(scholarshipRequestId)
                .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

        if (scholarshipRequest.getStatus() != UniversityScholarshipRequestStatus.APPROVED) {
            throw new RuntimeException("Fund Requests can be created only for approved University Scholarships");
        }

        UniversityScholarshipFundRequest fundRequest = null;
        if (StringUtils.hasText(dto.getRequestId())) {
            fundRequest = fundRequestRepository.findByFundRequestId(dto.getRequestId().trim()).orElse(null);
        } else if (dto.getId() != null) {
            fundRequest = fundRequestRepository.findById(dto.getId()).orElse(null);
        }

        if (fundRequest == null) {
            fundRequest = new UniversityScholarshipFundRequest();
            fundRequest.setFundRequestId(generateFundRequestId());
            fundRequest.setUniversityScholarshipRequest(scholarshipRequest);
            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.NEW);
        } else if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.NEW
                && fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.INCOMPLETE) {
            throw new RuntimeException("Only New or Incomplete Fund Requests can be edited");
        }

        int requestedPeriod = parseRequestedPeriod(dto.getRequestedPeriod());
        if (requestedPeriod <= 0) {
            throw new RuntimeException("Format must be: Semester 1 (e.g. Semester 1, Semester 2, ...)");
        }

        int years = parseInt(scholarshipRequest.getDuration());
        int totalSemesters = years > 0 ? years * 2 : 10;
        if (requestedPeriod > totalSemesters) {
            throw new RuntimeException("Requested Period cannot exceed total course semesters (Semester " + totalSemesters + ")");
        }

        final UniversityScholarshipFundRequest targetFundRequest = fundRequest;
        boolean duplicateExists = fundRequestRepository.findByUniversityScholarshipRequest(scholarshipRequest)
                .stream()
                .filter(fr -> targetFundRequest.getId() == null || !fr.getId().equals(targetFundRequest.getId()))
                .filter(fr -> fr.getStatus() != UniversityScholarshipFundRequestStatus.REJECTED
                        && fr.getStatus() != UniversityScholarshipFundRequestStatus.INACTIVE)
                .anyMatch(fr -> parseRequestedPeriod(fr.getRequestedPeriod()) == requestedPeriod);

        if (duplicateExists) {
            throw new RuntimeException("A fund request for this semester already exists for this scholarship");
        }

        double requestedAmount = dto.getRequestedAmount() != null ? dto.getRequestedAmount() : 0.0;
        double balanceAmount = getStoredTotalScholarshipAmount(scholarshipRequest)
                - calculateTotalDisbursedAmount(fundRequestRepository.findByUniversityScholarshipRequest(scholarshipRequest));

        if (requestedAmount <= 0 || requestedAmount > balanceAmount) {
            throw new RuntimeException("Amount cannot be more than the Balance Amount");
        }

        fundRequest.setRequestedDate(dto.getRequestedDate());
        fundRequest.setRequestedPeriod(dto.getRequestedPeriod());
        fundRequest.setRequestedAmount(requestedAmount);
        fundRequest.setDisbursedAmount(fundRequest.getDisbursedAmount());
        fundRequest.setDisbursementDate(fundRequest.getDisbursementDate());

        if (StringUtils.hasText(dto.getStatus())
                && UniversityScholarshipFundRequestStatus.INCOMPLETE.name().equals(dto.getStatus().trim().toUpperCase())) {
            if (!StringUtils.hasText(dto.getIncompleteReason())) {
                throw new RuntimeException("Incomplete reason is required");
            }

            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.INCOMPLETE);
            fundRequest.setIncompleteReason(dto.getIncompleteReason().trim());
        }

        return toFundRequestDto(fundRequestRepository.save(fundRequest));
    }

    private UniversityScholarshipFundRequest findFundRequestForScholarship(String scholarshipRequestId, String fundRequestId) {
        UniversityScholarshipRequest scholarshipRequest = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(scholarshipRequestId)
                .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

        UniversityScholarshipFundRequest fundRequest = fundRequestRepository.findByFundRequestId(fundRequestId)
                .orElseThrow(() -> new RuntimeException("Fund request not found"));

        if (fundRequest.getUniversityScholarshipRequest() == null
                || !fundRequest.getUniversityScholarshipRequest().getId().equals(scholarshipRequest.getId())) {
            throw new RuntimeException("Fund request does not belong to the selected scholarship");
        }

        return fundRequest;
    }

    //Submit Fund Request
    public UniversityScholarshipFundRequestDto submitFundRequest(String scholarshipRequestId, String fundRequestId) {
        UniversityScholarshipFundRequest fundRequest = findFundRequestForScholarship(scholarshipRequestId, fundRequestId);

        if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.NEW
                && fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.INCOMPLETE) {
            throw new RuntimeException("Only New or Incomplete Fund Requests can be submitted");
        }

        fundRequest.setStatus(UniversityScholarshipFundRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL);
        fundRequest.setIncompleteReason(null);
        return toFundRequestDto(fundRequestRepository.save(fundRequest));
    }
 
    //Mark Fund Request Incomplete
    public UniversityScholarshipFundRequestDto markFundRequestIncomplete(
            String scholarshipRequestId,
            String fundRequestId,
            String reason
    ) {
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("Incomplete reason is required");
        }

        UniversityScholarshipFundRequest fundRequest = findFundRequestForScholarship(scholarshipRequestId, fundRequestId);

        if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.NEW
                && fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.INCOMPLETE) {
            throw new RuntimeException("Only New or Incomplete Fund Requests can be marked as incomplete");
        }

        fundRequest.setStatus(UniversityScholarshipFundRequestStatus.INCOMPLETE);
        fundRequest.setIncompleteReason(reason.trim());
        return toFundRequestDto(fundRequestRepository.save(fundRequest));
    }

    /**
     * MMS45 — change a fund request's status from View Mode.
     *
     * Separate from {@link #updateFundRequestStatus} on purpose: that one carries the
     * MMS47 approve/reject decision and is gated on US_FUND_APPROVE, whereas this is
     * the administrative New/Inactive move and is gated on US_FUND_REOPEN /
     * US_FUND_SET_INACTIVE. Folding them together would let one right serve both.
     *
     * INCOMPLETE is absent from the table as a source status because the specification
     * for this screen does not list it; an incomplete fund request is resubmitted
     * through the ordinary Submit path. APPROVED is terminal — money has been released.
     */
    public UniversityScholarshipFundRequestDto changeFundRequestStatus(
            String scholarshipRequestId,
            String fundRequestId,
            String newStatusStr
    ) {
        UniversityScholarshipFundRequest fundRequest =
                findFundRequestForScholarship(scholarshipRequestId, fundRequestId);

        UniversityScholarshipFundRequestStatus newStatus;
        try {
            newStatus = UniversityScholarshipFundRequestStatus.valueOf(
                    newStatusStr == null ? "" : newStatusStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid status: " + newStatusStr);
        }

        UniversityScholarshipFundRequestStatus currentStatus = fundRequest.getStatus();
        if (currentStatus == null) {
            throw new RuntimeException("Current fund request status is unrecognized");
        }

        if (currentStatus == newStatus) {
            return toFundRequestDto(fundRequest);
        }

        if (!isFundStatusTransitionAllowed(currentStatus, newStatus)) {
            throw new RuntimeException(
                    "Cannot change status from " + currentStatus + " to " + newStatus);
        }

        fundRequest.setStatus(newStatus);

        // Back to New means the request is being reworked, so the note that explains
        // why it stopped no longer applies.
        if (newStatus == UniversityScholarshipFundRequestStatus.NEW) {
            fundRequest.setIncompleteReason(null);
            fundRequest.setDecisionReason(null);
        }

        return toFundRequestDto(fundRequestRepository.save(fundRequest));
    }

    /** The closed transition table behind {@link #changeFundRequestStatus}. */
    private boolean isFundStatusTransitionAllowed(
            UniversityScholarshipFundRequestStatus current,
            UniversityScholarshipFundRequestStatus next) {
        switch (current) {
            case NEW:
                return next == UniversityScholarshipFundRequestStatus.INACTIVE;
            case SUBMITTED_FOR_COMMITTEE_APPROVAL:
            case REJECTED:
                return next == UniversityScholarshipFundRequestStatus.NEW
                        || next == UniversityScholarshipFundRequestStatus.INACTIVE;
            case INACTIVE:
                return next == UniversityScholarshipFundRequestStatus.NEW;
            default:
                return false;
        }
    }

    // Update Fund Request Status
    public UniversityScholarshipFundRequestDto updateFundRequestStatus(
            String scholarshipRequestId,
            String fundRequestId,
            String status,
            String reason
    ) {
        if (!StringUtils.hasText(status)) {
            throw new RuntimeException("Status is required");
        }

        UniversityScholarshipFundRequest fundRequest = findFundRequestForScholarship(scholarshipRequestId, fundRequestId);
        UniversityScholarshipFundRequestStatus nextStatus;

        try {
            nextStatus = UniversityScholarshipFundRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid fund request status");
        }

        if (nextStatus == UniversityScholarshipFundRequestStatus.INCOMPLETE) {
            if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.NEW
                    && fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.INCOMPLETE) {
                throw new RuntimeException("Only New or Incomplete Fund Requests can be marked as incomplete");
            }

            if (!StringUtils.hasText(reason)) {
                throw new RuntimeException("Incomplete reason is required");
            }

            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.INCOMPLETE);
            fundRequest.setIncompleteReason(reason.trim());
            return toFundRequestDto(fundRequestRepository.save(fundRequest));
        }

        if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
            throw new RuntimeException("Only Submitted for Approval Fund Requests can be approved or rejected");
        }

        if (nextStatus == UniversityScholarshipFundRequestStatus.APPROVED) {
            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.APPROVED);
            fundRequest.setIncompleteReason(null);
            fundRequest.setDecisionReason(null);
            return toFundRequestDto(fundRequestRepository.save(fundRequest));
        }

        if (nextStatus == UniversityScholarshipFundRequestStatus.REJECTED) {
            if (!StringUtils.hasText(reason)) {
                throw new RuntimeException("Rejection reason is required");
            }

            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.REJECTED);
            fundRequest.setIncompleteReason(null);
            fundRequest.setDecisionReason(reason.trim());
            return toFundRequestDto(fundRequestRepository.save(fundRequest));
        }

        if (nextStatus != UniversityScholarshipFundRequestStatus.NEW) {
            throw new RuntimeException("Invalid status change for Submitted for Approval Fund Requests");
        }

        fundRequest.setStatus(UniversityScholarshipFundRequestStatus.NEW);
        fundRequest.setIncompleteReason(null);
        fundRequest.setDecisionReason(null);
        return toFundRequestDto(fundRequestRepository.save(fundRequest));
    }

    //Check minor account based on birth certificate number
    public Map<String, Object> checkMinorAccount(String birthCertificateNumber) {
        Optional<MinorAccount> minorAccount =
                minorAccountRepository.findByBirthCertificateNumber(birthCertificateNumber);

        if (minorAccount.isPresent()) {
            return Map.of(
                    "hasMinorAccount", "YES",
                    "remittedMonths", minorAccount.get().getRemittedMonths()
            );
        }

        return Map.of(
                "hasMinorAccount", "NO",
                "remittedMonths", "No minor account"
        );
    }

    // Check duplicate exam number
    public boolean isExamNoDuplicate(String examNumber) {
        return scholarshipRequestRepository.existsByExamNumber(examNumber);
    }
 
    // Get all universities based on table data
    public List<University> getAllUniversities() {
        return universityRepository.findAll();
    }
 
    // Get programs based on university ID
    public List<ProgramOptionDto> getProgramsByUniversity(Long universityId) {
        return universityProgramRepository.findByUniversityId(universityId)
                .stream()
                .map(up -> new ProgramOptionDto(
                        up.getProgram().getId(),
                        up.getProgram().getName(),
                        up.getDuration()
                ))
                .toList();
    }

    // Get duration based on university ID and program ID
    public Integer getDuration(Long universityId, Long programId) {
        UniversityProgram up = universityProgramRepository
                .findByUniversityIdAndProgramId(universityId, programId)
                .orElseThrow(() -> new RuntimeException("University-program mapping not found"));

        return up.getDuration();
    }
 
    // Get all banks based on table data
    public List<Bank> getAllBanks() {
        return bankRepository.findAll();
    }

    // Get branches based on bank ID
    public List<Branch> getBranchesByBank(Long bankId) {
        return branchRepository.findByBankId(bankId);
    }

    //Generate unique University Scholarship Request ID
    private String generateUniversityScholarshipRequestID() {
        long nextNumber = scholarshipRequestRepository.count() + 1;
        return String.format("USR-%03d", nextNumber);
    }

    // Save university scholarship request
    public UniversityScholarshipRequest saveRequest(UniversityScholarshipRequestDto dto) {
        
        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        validateMemberActiveOnExamLastDate(dto);
        validateMembershipDuration(dto);
        validateScholarshipRemittanceMonths(dto);
        validateRemainingMonthsSettled(dto);
        validateAnotherApprovedScholarshipWithinYear(dto);
        

        if (scholarshipRequestRepository.existsByExamNumber(dto.getExamNo())) {
            throw new RuntimeException(
                    "Entered Examination Number is duplicating with another Scholarship Request"
            );
        }

        if (dto.getUniversity() == null || dto.getUniversity().isBlank()) {
            throw new RuntimeException("University is required");
        }

        if (dto.getProgram() == null || dto.getProgram().isBlank()) {
            throw new RuntimeException("Program is required");
        }

        University university = universityRepository.findById(Long.parseLong(dto.getUniversity()))
                .orElseThrow(() -> new RuntimeException("University not found"));

        Program program = programRepository.findById(Long.parseLong(dto.getProgram()))
                .orElseThrow(() -> new RuntimeException("Program not found"));

        Bank bank = null;
        if (dto.getBank() != null && !dto.getBank().isBlank()) {
            Long bankId = Long.parseLong(dto.getBank());
            bank = bankRepository.findById(bankId)
                    .orElseThrow(() -> new RuntimeException("Bank not found"));
        }

        Branch branch = null;
        if (dto.getBranch() != null && !dto.getBranch().isBlank()) {
            Long branchId = Long.parseLong(dto.getBranch());
            branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
        }

        UniversityScholarshipRequest request = new UniversityScholarshipRequest();
        
        request.setMember(member);
        request.setRequestDate(dto.getRequestDate());
        request.setStudentName(dto.getStudentName());
        request.setNic(dto.getNic());
        request.setBcNo(dto.getBcNo());
        request.setAddress(dto.getAddress());
        request.setMobile(dto.getMobile());
        
        // Set applicant type based on isSchoolApplicant field
        if (Boolean.TRUE.equals(dto.getIsSchoolApplicant())) {
            request.setApplicantType(ApplicantType.SCHOOL_APPICANT);
        } else {
            request.setApplicantType(ApplicantType.PRIVATE_APPLICANT);
        }

        request.setExamYear(dto.getExamYear());
        request.setExamNo(dto.getExamNo());
        request.setZScore(dto.getZScore());

        request.setUniversity(university);
        request.setProgram(program);
        request.setDuration(dto.getDuration());

        request.setAcademicYearStart(dto.getAcademicYearStart());
        request.setAccountNo(dto.getAccountNo());
        request.setSpecialDegree(Boolean.TRUE.equals(dto.getSpecialDegree()));

        request.setBank(bank);
        request.setBranch(branch);

        // followDeviationProcess flag (optional, future UI/backend control)
        boolean followDeviation = Boolean.TRUE.equals(dto.getFollowDeviationProcess());

        // Determine eligibility period from ScholarshipConfig
        int eligibilityMonths = scholarshipConfigRepository.findByConfigKey("scholarship.eligibility.period.months")
                .map(com.memberconnect.backend.model.ScholarshipConfig::getConfigValue)
                .orElse(6);

        // If request date is provided and exam info is available, check eligibility window
        try {
                UniversityScholarshipExamMaster examMaster = examMasterRepository
                        .findByExamYear(dto.getExamYear())
                        .orElse(null);

                if (dto.getRequestDate() != null && examMaster != null) {
                        LocalDate examLastDate = examMaster.getExamLastDate();
                        // Eligible window: from examLastDate to examLastDate + eligibilityMonths (default 12 = 1 year)
                        LocalDate latestAllowed = examLastDate.plusMonths(eligibilityMonths);

                        LocalDate reqDate = dto.getRequestDate();
                        // Deviation if request date is before exam last date OR after the 1-year window
                        if (reqDate.isBefore(examLastDate) || reqDate.isAfter(latestAllowed)) {
                                followDeviation = true;
                        }
                }
        } catch (Exception ignored) {
        // If any config or exam master lookup fails, do not block save; leave followDeviation as provided
        }

        request.setFollowDeviationProcess(followDeviation);

        // Location comes from the member's administering District Office, not from the
        // logged-in user — a member may apply at any office (MMS21) but the request
        // belongs to the branch that holds their membership.
        request.setSubmissionLocation(member.getSubmissionLocation());
        com.memberconnect.backend.model.User currentUser = currentUserService.current();
        request.setCreatedBy(currentUser != null ? currentUser.getUsername() : null);
        request.setCreatedAt(LocalDateTime.now());

        // Set status NEW
        request.setStatus(UniversityScholarshipRequestStatus.NEW);
        
        // Check minor account if hasMinorAccount is not provided
        if (dto.getHasMinorAccount() == null) {
            Map<String, Object> minorData = checkMinorAccount(dto.getBcNo());

            request.setHasMinorAccount(
                "YES".equals(minorData.get("hasMinorAccount"))
                    ? com.memberconnect.backend.enums.MinorAccount.YES
                    : com.memberconnect.backend.enums.MinorAccount.NO
            );

            request.setMinorAccountMonths((String) minorData.get("remittedMonths"));
        } else {
            request.setHasMinorAccount(
                "YES".equalsIgnoreCase(dto.getHasMinorAccount().trim())
                    ? com.memberconnect.backend.enums.MinorAccount.YES
                    : com.memberconnect.backend.enums.MinorAccount.NO
            );
            request.setMinorAccountMonths(dto.getMinorAccountMonths());
        }

        updateTotalScholarshipAmount(request);
        request.setUniversityScholarshipRequestID(generateUniversityScholarshipRequestID());
        return scholarshipRequestRepository.save(request);
    }   
    
    public UniversityScholarshipRequest updateRequestByRequestId(String requestId, UniversityScholarshipRequestDto dto) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                        .findByUniversityScholarshipRequestID(requestId)
                        .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

        if (request.getStatus() == UniversityScholarshipRequestStatus.APPROVED) {
                throw new RuntimeException("Approved University Scholarship records cannot be edited from the normal edit screen");
        }

        if (dto.getMemberId() != null) {
                Member member = memberRepository.findByMemberId(dto.getMemberId())
                        .orElseThrow(() -> new RuntimeException("Member not found"));
                request.setMember(member);
        }

        if (dto.getRequestDate() != null) {
                request.setRequestDate(dto.getRequestDate());
        }

        if (StringUtils.hasText(dto.getStudentName())) {
                request.setStudentName(dto.getStudentName().trim());
        }

        if (StringUtils.hasText(dto.getNic())) {
                request.setNic(dto.getNic().trim());
        }

        if (StringUtils.hasText(dto.getBcNo())) {
                request.setBcNo(dto.getBcNo().trim());
        }

        if (StringUtils.hasText(dto.getAddress())) {
                request.setAddress(dto.getAddress().trim());
        }

        if (StringUtils.hasText(dto.getMobile())) {
                request.setMobile(dto.getMobile().trim());
        }

        if (dto.getIsSchoolApplicant() != null) {
                request.setApplicantType(Boolean.TRUE.equals(dto.getIsSchoolApplicant())
                        ? ApplicantType.SCHOOL_APPICANT
                        : ApplicantType.PRIVATE_APPLICANT);
        }

        if (StringUtils.hasText(dto.getExamYear())) {
                request.setExamYear(dto.getExamYear().trim());
        }

        if (StringUtils.hasText(dto.getExamNo())) {
                String examNo = dto.getExamNo().trim();
                if (!examNo.equals(request.getExamNo()) && scholarshipRequestRepository.existsByExamNumber(examNo)) {
                        throw new RuntimeException("Entered Examination Number is duplicating with another Scholarship Request");
                }
                request.setExamNo(examNo);
        }

        if (StringUtils.hasText(dto.getZScore())) {
                request.setZScore(dto.getZScore().trim());
        }

        if (StringUtils.hasText(dto.getDuration())) {
                request.setDuration(dto.getDuration().trim());
        }

        if (dto.getAcademicYearStart() != null) {
                request.setAcademicYearStart(dto.getAcademicYearStart());
        }

        if (StringUtils.hasText(dto.getAccountNo())) {
                request.setAccountNo(dto.getAccountNo().trim());
        }

        if (StringUtils.hasText(dto.getUniversity())) {
                University university = universityRepository.findById(Long.parseLong(dto.getUniversity().trim()))
                        .orElseThrow(() -> new RuntimeException("University not found"));
                request.setUniversity(university);
        }

        if (StringUtils.hasText(dto.getProgram())) {
                Program program = programRepository.findById(Long.parseLong(dto.getProgram().trim()))
                        .orElseThrow(() -> new RuntimeException("Program not found"));
                request.setProgram(program);
        }

        if (dto.getBank() != null) {
                if (StringUtils.hasText(dto.getBank())) {
                        Bank bank = bankRepository.findById(Long.parseLong(dto.getBank().trim()))
                                .orElseThrow(() -> new RuntimeException("Bank not found"));
                        request.setBank(bank);
                } else {
                        request.setBank(null);
                }
        }

        if (dto.getBranch() != null) {
                if (StringUtils.hasText(dto.getBranch())) {
                        Branch branch = branchRepository.findById(Long.parseLong(dto.getBranch().trim()))
                                .orElseThrow(() -> new RuntimeException("Branch not found"));
                        request.setBranch(branch);
                } else {
                        request.setBranch(null);
                }
        }

        if (StringUtils.hasText(dto.getHasMinorAccount())) {
                String hasMinor = dto.getHasMinorAccount().trim();
                request.setHasMinorAccount("YES".equalsIgnoreCase(hasMinor)
                        ? com.memberconnect.backend.enums.MinorAccount.YES
                        : com.memberconnect.backend.enums.MinorAccount.NO);
        }

        if (dto.getMinorAccountMonths() != null) {
                request.setMinorAccountMonths(dto.getMinorAccountMonths());
        }

        if (dto.getFollowDeviationProcess() != null) {
                request.setFollowDeviationProcess(dto.getFollowDeviationProcess());
        }

        if (dto.getSpecialDegree() != null) {
                request.setSpecialDegree(dto.getSpecialDegree());
        }

        updateTotalScholarshipAmount(request);
        return scholarshipRequestRepository.save(request);
     }

    public UniversityScholarshipRequest updateApprovedDetailsByRequestId(String requestId, UniversityScholarshipRequestDto dto) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

        if (request.getStatus() != UniversityScholarshipRequestStatus.APPROVED) {
                throw new RuntimeException("Only approved University Scholarship records can be updated from this screen");
        }

        if (dto.getAcademicYearStart() != null) {
                request.setAcademicYearStart(dto.getAcademicYearStart());
        }

        if (dto.getBank() != null) {
                if (StringUtils.hasText(dto.getBank())) {
                        Bank bank = bankRepository.findById(Long.parseLong(dto.getBank().trim()))
                                .orElseThrow(() -> new RuntimeException("Bank not found"));
                        request.setBank(bank);
                } else {
                        request.setBank(null);
                }
        }

        if (dto.getBranch() != null) {
                if (StringUtils.hasText(dto.getBranch())) {
                        Branch branch = branchRepository.findById(Long.parseLong(dto.getBranch().trim()))
                                .orElseThrow(() -> new RuntimeException("Branch not found"));
                        request.setBranch(branch);
                } else {
                        request.setBranch(null);
                }
        }

        if (dto.getAccountNo() != null) {
                request.setAccountNo(dto.getAccountNo().trim());
        }

        if (StringUtils.hasText(dto.getHasMinorAccount())) {
                String hasMinor = dto.getHasMinorAccount().trim();
                request.setHasMinorAccount("YES".equalsIgnoreCase(hasMinor)
                        ? com.memberconnect.backend.enums.MinorAccount.YES
                        : com.memberconnect.backend.enums.MinorAccount.NO);
        }

        if (dto.getMinorAccountMonths() != null) {
                request.setMinorAccountMonths(dto.getMinorAccountMonths().trim());
        }

        if (dto.getSpecialDegree() != null) {
                request.setSpecialDegree(dto.getSpecialDegree());
        }

        updateTotalScholarshipAmount(request);
        return scholarshipRequestRepository.save(request);
     }
 
    // Submit university scholarship request
    public UniversityScholarshipRequest submitRequest(String requestId) {

        UniversityScholarshipRequest request = scholarshipRequestRepository.findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Check required fields
        if (request.getStudentName() == null || request.getExamNo() == null) {
            throw new RuntimeException("Fill all required fields before submitting");
        }

        // Update status
        request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL);

        return scholarshipRequestRepository.save(request);
    }

    /**
     * MMS26 — the University Scholarship Committee clears a request for the Board.
     *
     * Previously this method also handled ADDED_TO_*_LIST -> APPROVED, which meant one
     * endpoint served two different authority levels and gave a single caller a route
     * from committee straight to final approval, skipping the Board Meeting entirely —
     * no actual meeting date, no scanned report, no summary confirmation. That branch
     * is gone: final approval now happens only through processApprovalDecisions.
     */
    public UniversityScholarshipRequest committeeApproveRequest(String requestId) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getStatus() != UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
            throw new RuntimeException(
                    "Only requests submitted for Committee Approval can be approved by the Committee");
        }

        // The Board track is decided here rather than at save time, per 3.2.1: the
        // deviation check runs "once the approval is given by the University
        // Scholarship Committee".
        if (Boolean.TRUE.equals(request.getFollowDeviationProcess())) {
            request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL);
        } else {
            request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_BOARD_APPROVAL);
        }

        stampCommitteeDecision(request);

        return scholarshipRequestRepository.save(request);
    }

    /** MMS26 — the Committee rejects a request outright. */
    public UniversityScholarshipRequest committeeRejectRequest(String requestId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("A reason is required to reject a scholarship request");
        }

        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Previously this ran straight off the controller with no status check at all,
        // so an already-Approved award could be flipped to Rejected.
        if (request.getStatus() != UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
            throw new RuntimeException(
                    "Only requests submitted for Committee Approval can be rejected by the Committee");
        }

        request.setStatus(UniversityScholarshipRequestStatus.REJECTED);
        request.setRejectReason(reason.trim());
        stampCommitteeDecision(request);

        return scholarshipRequestRepository.save(request);
    }

    private void stampCommitteeDecision(UniversityScholarshipRequest request) {
        com.memberconnect.backend.model.User currentUser = currentUserService.current();
        request.setCommitteeDecisionBy(currentUser != null ? currentUser.getUsername() : null);
        request.setCommitteeDecisionAt(LocalDateTime.now());
    }
    
    // Mark request as incomplete with reason
    public UniversityScholarshipRequest markAsIncomplete(String requestId, String reason) {
                UniversityScholarshipRequest request = scholarshipRequestRepository
                        .findByUniversityScholarshipRequestID(requestId)
                        .orElseThrow(() -> new RuntimeException("Request not found"));

                if (request.getStatus() != UniversityScholarshipRequestStatus.NEW) {
                        throw new RuntimeException("Only NEW requests can be marked as incomplete");
                }

                request.setStatus(UniversityScholarshipRequestStatus.INCOMPLETE);
                request.setIncompleteReason(reason);

                return scholarshipRequestRepository.save(request);
    }

    // Attach scholarship requests to a normal board meeting for approval
    @Transactional
    public void attachBoardMeeting(Map<String, Object> payload) {
        Object meetingIdObj = payload.get("boardMeetingId");
        if (meetingIdObj == null) {
            throw new RuntimeException("Board Meeting ID is required");
        }
        Long boardMeetingId = Long.valueOf(meetingIdObj.toString());
        BoardMeeting boardMeeting = boardMeetingRepository.findById(boardMeetingId)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));

        java.util.List<String> requestIds = (java.util.List<String>) payload.get("requestIds");
        if (requestIds == null || requestIds.isEmpty()) {
            throw new RuntimeException("No scholarship requests specified");
        }

        String approvalListId = generateNextApprovalListId("USNL-");
        for (String requestId : requestIds) {
            UniversityScholarshipRequest request = scholarshipRequestRepository.findByUniversityScholarshipRequestID(requestId)
                    .orElseThrow(() -> new RuntimeException("Scholarship Request not found: " + requestId));
            request.setBoardMeeting(boardMeeting);
            request.setApprovalListId(approvalListId);
            request.setStatus(UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST);
            scholarshipRequestRepository.save(request);
        }
    }

    // Attach scholarship requests to a deviation board meeting for approval
    @Transactional
    public void attachDeviationBoardMeeting(Map<String, Object> payload) {
        Object meetingIdObj = payload.get("boardMeetingId");
        if (meetingIdObj == null) {
            throw new RuntimeException("Board Meeting ID is required");
        }
        Long boardMeetingId = Long.valueOf(meetingIdObj.toString());
        BoardMeeting boardMeeting = boardMeetingRepository.findById(boardMeetingId)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));

        java.util.List<String> requestIds = (java.util.List<String>) payload.get("requestIds");
        if (requestIds == null || requestIds.isEmpty()) {
            throw new RuntimeException("No scholarship requests specified");
        }

        String approvalListId = generateNextApprovalListId("USDL-");
        for (String requestId : requestIds) {
            UniversityScholarshipRequest request = scholarshipRequestRepository.findByUniversityScholarshipRequestID(requestId)
                    .orElseThrow(() -> new RuntimeException("Scholarship Request not found: " + requestId));
            request.setBoardMeeting(boardMeeting);
            request.setApprovalListId(approvalListId);
            request.setStatus(UniversityScholarshipRequestStatus.ADDED_TO_DEVIATION_BOARD_APPROVAL_LIST);
            scholarshipRequestRepository.save(request);
        }
    }
    
    private synchronized String generateNextApprovalListId(String prefix) {
        List<UniversityScholarshipRequest> requests = scholarshipRequestRepository.findAll();
        int maxVal = 0;
        for (UniversityScholarshipRequest req : requests) {
            String listId = req.getApprovalListId();
            if (listId != null && listId.startsWith(prefix)) {
                try {
                    String numPart = listId.substring(prefix.length());
                    int val = Integer.parseInt(numPart);
                    if (val > maxVal) {
                        maxVal = val;
                    }
                } catch (NumberFormatException e) {

                }
            }
        }
        return String.format("%s%03d", prefix, maxVal + 1);
    }

    /**
     * Deletes a Normal Approval List by rolling back all attached requests to
     * SUBMITTED_FOR_NORMAL_BOARD_APPROVAL and detaching them from the board meeting.
     */
    @Transactional
    public void deleteApprovalList(String approvalListId) {
        List<UniversityScholarshipRequest> requests =
                scholarshipRequestRepository.findByApprovalListId(approvalListId);

        if (requests.isEmpty()) {
            throw new RuntimeException("No approval list found with ID: " + approvalListId);
        }

        for (UniversityScholarshipRequest request : requests) {
            if (request.getStatus() == UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST) {
                request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_BOARD_APPROVAL);
                request.setBoardMeeting(null);
                request.setApprovalListId(null);
                scholarshipRequestRepository.save(request);
            }
        }
    }

    /**
     * Deletes a Deviation Approval List by rolling back all attached requests to
     * SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL and detaching them from the board meeting.
     */
    @Transactional
    public void deleteDeviationApprovalList(String approvalListId) {
        List<UniversityScholarshipRequest> requests =
                scholarshipRequestRepository.findByApprovalListId(approvalListId);

        if (requests.isEmpty()) {
            throw new RuntimeException("No deviation approval list found with ID: " + approvalListId);
        }

        for (UniversityScholarshipRequest request : requests) {
            if (request.getStatus() == UniversityScholarshipRequestStatus.ADDED_TO_DEVIATION_BOARD_APPROVAL_LIST) {
                request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL);
                request.setBoardMeeting(null);
                request.setApprovalListId(null);
                scholarshipRequestRepository.save(request);
            }
        }
    }

    /**
     * Processes normal board approvals with file upload.
     */
    @Transactional
    public void processApprovals(String dataJson, MultipartFile file) {
        processApprovalDecisions(dataJson, file, UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST);
    }

    /**
     * Processes deviation board approvals with file upload.
     */
    @Transactional
    public void processDeviationApprovals(String dataJson, MultipartFile file) {
        processApprovalDecisions(dataJson, file, UniversityScholarshipRequestStatus.ADDED_TO_DEVIATION_BOARD_APPROVAL_LIST);
    }

    // process approvals
    @SuppressWarnings("unchecked")
    private void processApprovalDecisions(String dataJson, MultipartFile file, UniversityScholarshipRequestStatus expectedStatus) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(dataJson, Map.class);
            
            String approvalListId = (String) payload.get("approvalListId");
            String actualDateStr = (String) payload.get("actualBoardMeetingDate");
            String comment = (String) payload.get("comment");
            List<Map<String, Object>> decisions = (List<Map<String, Object>>) payload.get("decisions");

            String scannedReportPath = null;
            if (file != null && !file.isEmpty()) {
                try {
                    scannedReportPath = s3Service.uploadFile(file);
                } catch (Exception e) {
                    System.err.println("S3 upload failed: " + e.getMessage());
                    scannedReportPath = "uploads/" + file.getOriginalFilename();
                }
            }

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            com.memberconnect.backend.model.User currentUser = currentUserService.current();
            String processedBy = currentUser != null ? currentUser.getUsername() : null;

            for (Map<String, Object> dec : decisions) {
                String requestId = (String) dec.get("requestId");
                String action = (String) dec.get("action");
                String reason = (String) dec.get("reason");

                UniversityScholarshipRequest request = scholarshipRequestRepository
                        .findByUniversityScholarshipRequestID(requestId)
                        .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

                // The expectedStatus argument used to be accepted and never read, so the
                // normal and deviation endpoints behaved identically and neither checked
                // that the request was actually on a list. A crafted payload could take a
                // NEW request straight to APPROVED, skipping Committee and Board both.
                if (request.getStatus() != expectedStatus) {
                    throw new RuntimeException("Request " + requestId + " is not awaiting this approval list"
                            + " (expected " + expectedStatus + ", found " + request.getStatus() + ")");
                }

                if (approvalListId != null && !approvalListId.equals(request.getApprovalListId())) {
                    throw new RuntimeException("Request " + requestId
                            + " does not belong to approval list " + approvalListId);
                }

                if ("reject".equalsIgnoreCase(action)) {
                    // MMS32/MMS39 make the rejection reason mandatory.
                    if (!StringUtils.hasText(reason)) {
                        throw new RuntimeException("A rejection reason is required for request " + requestId);
                    }
                    request.setStatus(UniversityScholarshipRequestStatus.REJECTED);
                    request.setRejectReason(reason.trim());
                    System.out.println("SIMULATING NOTIFICATION: SMS sent to Student " + request.getStudentName() + " and Member: Request rejected. Reason: " + reason);
                    System.out.println("SIMULATING NOTIFICATION: Email sent to Student " + request.getStudentName() + " and Member: Request rejected. Reason: " + reason);
                } else {
                    request.setStatus(UniversityScholarshipRequestStatus.APPROVED);
                    System.out.println("SIMULATING NOTIFICATION: SMS sent to Student " + request.getStudentName() + " and Member: Request approved.");
                    System.out.println("SIMULATING NOTIFICATION: Email sent to Student " + request.getStudentName() + " and Member: Request approved.");
                }

                // MMS33/MMS40 require the processing user's name to be displayed on the
                // list afterwards. This was hardcoded to the literal "user1", which made
                // every board decision in the system unattributable.
                request.setProcessedBy(processedBy);
                request.setProcessedAt(now);
                if (scannedReportPath != null) {
                    request.setScannedReportPath(scannedReportPath);
                }
                if (actualDateStr != null && !actualDateStr.isBlank()) {
                    try {
                        request.setActualBoardMeetingDate(LocalDate.parse(actualDateStr));
                    } catch (Exception ignored) {}
                }

                scholarshipRequestRepository.save(request);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process decisions: " + e.getMessage(), e);
        }
    }

    public byte[] downloadFile(String fileName) {
        try {
            return s3Service.downloadFile(fileName);
        } catch (Exception e) {
            try {
                return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fileName));
            } catch (Exception ex) {
                throw new RuntimeException("File not found: " + fileName);
            }
        }
    }

    /**
     * MMS25 — change a request's status from View Mode.
     *
     * Mirrors Grade5ScholarshipService.changeRequestStatus. The permitted moves are a
     * closed table rather than "anything the caller asks for": every route out of a
     * submitted or decided state goes back to NEW or to INACTIVE, and APPROVED is
     * terminal — an awarded scholarship must not be walked backwards once the Board
     * has granted it, and the ADDED_TO_*_LIST states belong to an in-flight Board
     * Meeting, which is released by deleting the list, not by editing the request.
     */
    public UniversityScholarshipRequest changeRequestStatus(String requestId, String newStatusStr) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("University Scholarship request not found"));

        UniversityScholarshipRequestStatus newStatus;
        try {
            newStatus = UniversityScholarshipRequestStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new RuntimeException("Invalid status: " + newStatusStr);
        }

        UniversityScholarshipRequestStatus currentStatus = request.getStatus();
        if (currentStatus == null) {
            throw new RuntimeException("Current request status is unrecognized");
        }

        if (currentStatus == newStatus) {
            return request;
        }

        if (!isUniversityStatusTransitionAllowed(currentStatus, newStatus)) {
            throw new RuntimeException(
                    "Cannot change status from " + currentStatus + " to " + newStatus);
        }

        // A request already picked up onto a Board approval list cannot be pulled back
        // to New by editing it — Head Office may be mid-way through a Board Meeting
        // with it on the printed list. Deleting the list is the supported release.
        if (newStatus == UniversityScholarshipRequestStatus.NEW
                && request.getApprovalListId() != null
                && !request.getApprovalListId().isBlank()) {
            throw new RuntimeException(
                    "This request is attached to approval list " + request.getApprovalListId()
                            + " and cannot be returned to New. Remove it from the list first.");
        }

        request.setStatus(newStatus);

        // Returning to New clears the decision trail that put it where it was, so the
        // request reads as genuinely fresh rather than carrying a stale rejection note.
        if (newStatus == UniversityScholarshipRequestStatus.NEW) {
            request.setIncompleteReason(null);
            request.setRejectReason(null);
        }

        return scholarshipRequestRepository.save(request);
    }

    /** The closed transition table behind {@link #changeRequestStatus}. */
    private boolean isUniversityStatusTransitionAllowed(
            UniversityScholarshipRequestStatus current,
            UniversityScholarshipRequestStatus next) {
        switch (current) {
            case NEW:
                return next == UniversityScholarshipRequestStatus.INACTIVE;
            case INCOMPLETE:
            case SUBMITTED_FOR_COMMITTEE_APPROVAL:
            case SUBMITTED_FOR_NORMAL_BOARD_APPROVAL:
            case SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL:
            case REJECTED:
                return next == UniversityScholarshipRequestStatus.NEW
                        || next == UniversityScholarshipRequestStatus.INACTIVE;
            case INACTIVE:
                return next == UniversityScholarshipRequestStatus.NEW;
            default:
                return false;
        }
    }

}
