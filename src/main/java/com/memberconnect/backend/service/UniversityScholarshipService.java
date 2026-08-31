package com.memberconnect.backend.service;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.dto.UniversityScholarshipFundRequestDto;
import com.memberconnect.backend.dto.UniversityScholarshipFundRequestListDto;
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
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Comparator;
import java.util.ArrayList;
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

    @Autowired
    private MemberStatusHistoryService memberStatusHistoryService;

    @Autowired
    private NotificationService notificationService;

    @Value("${scholarship.required.remitted.months}")
    private int requiredRemittedMonths;

    @Value("${scholarship.lookback.years}")
    private int lookbackYears;

    @Value("${scholarship.finance.validation.source:member}")
    private String financeValidationSource;

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
   
    // Validates that the member was Active on the selected exam's last date.
    private void validateMemberActiveDuringExam(String memberId, String examYear) {
        if (!StringUtils.hasText(memberId) || !StringUtils.hasText(examYear)) {
            return;
        }

        UniversityScholarshipExamMaster examMaster = examMasterRepository
                .findByExamYear(examYear)
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamLastDate();
        if (examLastDate == null) {
            throw new RuntimeException("Selected exam last date not found in Exam Master");
        }

        if (memberStatusHistoryService.wasNotActiveOn(memberId, examLastDate)) {
            throw new RuntimeException(
                    "The University Scholarship Request cannot be saved. The Member is not Active during the selected Exam"
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
    
    //temporary check
    private void validateMemberFinanceEligibility(Member member) {

        if (!member.isRemittance()) {
            throw new RuntimeException(
                    "Scholarship amount was not continuously remitted from the Member for the specified period"
            );
        }

        if (!member.isSettlement()) {
            throw new RuntimeException(
                    "The Scholarship Amounts were not settled for some months"
            );
        }
    }

    private void validateFinanceEligibility(UniversityScholarshipRequestDto dto, Member member) {
        if ("finance".equalsIgnoreCase(financeValidationSource)) {
            validateScholarshipRemittanceMonths(dto);
            validateRemainingMonthsSettled(dto);
            return;
        }

        validateMemberFinanceEligibility(member);
    }

    /**
     * Validate that the member holds no other approved scholarship for the same exam
     * year, approved within a year of that exam.
     *
     * The window runs from the last day of the month the exam's last date falls in -
     * the exam is treated as finishing with its month rather than on the day the last
     * paper happens to be sat - and forward one year from there. A scholarship counts
     * as inside the window when the board's decision on it was recorded in that period.
     */
    private void validateAnotherApprovedScholarshipWithinYear(UniversityScholarshipRequestDto dto) {

        UniversityScholarshipExamMaster examMaster = examMasterRepository
                .findByExamYear(dto.getExamYear())
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamLastDate();
        if (examLastDate == null) {
            throw new RuntimeException("Selected exam last date not found in Exam Master");
        }

        Member member = memberRepository.findByMemberId(dto.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        LocalDate examMonthEnd = examLastDate.with(TemporalAdjusters.lastDayOfMonth());

        boolean exists = scholarshipRequestRepository
            .existsApprovedForExamYearProcessedBetween(
                    member.getMemberId(),
                    UniversityScholarshipRequestStatus.APPROVED,
                    dto.getExamYear(),
                    examMonthEnd.atStartOfDay(),
                    examMonthEnd.plusYears(1).atTime(LocalTime.MAX)
            );

        if (exists) {
            throw new RuntimeException(
                    "Another University Scholarship was approved for the Member within a year"
            );
        }
    }

    // Get all scholarship requests with member and university details
    public List<UniversityScholarshipListDto> getAllScholarshipRequests() {
        CurrentUserService.LocationScope locationScope =
                currentUserService.resolveLocationScope(null);

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

    /**
     * The University Scholarship list, narrowed server-side (MMS21-MMS48).
     *
     * The screen used to call getAllScholarshipRequests() and apply Location, Status,
     * Received On, Search and Sort in the browser, so "Retrieve" fetched every request
     * in the caller's scope on every press and threw most of them away.
     *
     * Location goes through resolveLocationScope, so a District Office caller stays
     * pinned to their own district whatever they ask for - the same rule the unfiltered
     * list already applied.
     */
    public List<UniversityScholarshipListDto> searchScholarshipRequests(
            List<String> locations,
            List<String> statuses,
            String receivedOn,
            String fromDate,
            String toDate,
            String search,
            String sortBy,
            String sortDirection) {

        CurrentUserService.LocationScope locationScope =
                currentUserService.resolveLocationScope(locations);

        if (locationScope.showsNothing()) {
            return List.of();
        }

        LocalDate[] range = resolveReceivedOnRange(receivedOn, fromDate, toDate);
        LocalDate from = range[0];
        LocalDate to = range[1];

        Set<String> wantedStatuses = normaliseStatusFilter(statuses);
        String needle = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase();

        List<UniversityScholarshipListDto> matched = scholarshipRequestRepository.findAll()
                .stream()
                .filter(request -> currentUserService.matchesScope(
                        locationScope, request.getSubmissionLocation()))
                .map(this::toListDto)
                .filter(dto -> wantedStatuses.isEmpty()
                        || wantedStatuses.contains(canonicalStatus(dto.getStatus())))
                .filter(dto -> withinRange(dto.getRequestDate(), from, to))
                .filter(dto -> matchesScholarshipSearch(dto, needle))
                .collect(Collectors.toCollection(ArrayList::new));

        matched.sort(scholarshipComparator(sortBy, sortDirection));
        return matched;
    }

    /**
     * MMS's "Application Received On" options as a concrete date range.
     *
     * ALL_DAYS and a DATE_PERIOD missing either bound both mean "no limit", which is why
     * nulls come back rather than defaults.
     */
    private LocalDate[] resolveReceivedOnRange(String receivedOn, String fromDate, String toDate) {
        String period = receivedOn == null ? "all" : receivedOn.trim().toLowerCase();
        LocalDate today = LocalDate.now();

        switch (period) {
            case "thismonth":
            case "this_month":
                return new LocalDate[] { today.withDayOfMonth(1), today };
            case "thisandlastmonth":
            case "this_and_last_month":
                return new LocalDate[] { today.minusMonths(1).withDayOfMonth(1), today };
            case "dateperiod":
            case "date_period":
                return new LocalDate[] { parseDateOrNull(fromDate), parseDateOrNull(toDate) };
            default:
                return new LocalDate[] { null, null };
        }
    }

    private LocalDate parseDateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * A row with no requested date is excluded from a bounded period rather than
     * silently included in every one the user picks.
     */
    private boolean withinRange(LocalDate value, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }

    /**
     * Statuses arrive from the screen already stripped of spaces and underscores and
     * lower-cased, so both sides are reduced to the same shape before comparing.
     */
    private Set<String> normaliseStatusFilter(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }
        return statuses.stream()
                .filter(status -> status != null && !status.isBlank())
                .map(UniversityScholarshipService::canonicalStatus)
                .collect(Collectors.toSet());
    }

    private static String canonicalStatus(String status) {
        return status == null ? "" : status.toLowerCase().replaceAll("[\\s_]+", "");
    }

    /** MMS21: student name, member name, member number, request id, NIC and exam number. */
    private boolean matchesScholarshipSearch(UniversityScholarshipListDto dto, String needle) {
        if (needle == null) {
            return true;
        }
        return containsIgnoreCase(dto.getStudentName(), needle)
                || containsIgnoreCase(dto.getMemberName(), needle)
                || containsIgnoreCase(dto.getMemberId(), needle)
                || containsIgnoreCase(dto.getRequestId(), needle)
                || containsIgnoreCase(dto.getNic(), needle)
                || containsIgnoreCase(dto.getExamNumber(), needle);
    }

    private boolean containsIgnoreCase(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase().contains(lowercaseNeedle);
    }

    /** Nulls sort last in both directions, so an undated row never heads the list. */
    private Comparator<UniversityScholarshipListDto> scholarshipComparator(
            String sortBy, String sortDirection) {

        String key = sortBy == null ? "requested-date" : sortBy.trim().toLowerCase();

        Comparator<UniversityScholarshipListDto> comparator = switch (key) {
            case "status" -> Comparator.comparing(
                    UniversityScholarshipListDto::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "member-id", "memberid" -> Comparator.comparing(
                    UniversityScholarshipListDto::getMemberId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "scholarship-id", "scholarshipid" -> Comparator.comparing(
                    dto -> dto.getRequestId() != null ? dto.getRequestId() : String.valueOf(dto.getId()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(
                    UniversityScholarshipListDto::getRequestDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };

        return "desc".equalsIgnoreCase(sortDirection) ? comparator.reversed() : comparator;
    }

    /**
     * The University Scholarship Fund Requests list, narrowed server-side.
     *
     * A dedicated search rather than a flag on the scholarship one: fund requests are a
     * different entity with their own status enum, and sharing an endpoint would mean
     * one route returning two shapes.
     *
     * The screen previously fetched every scholarship request with its fund requests
     * nested inside, flattened them in the browser, and filtered there - so it pulled
     * the whole scholarship module down to render a page of ten fund requests.
     */
    public List<UniversityScholarshipFundRequestListDto> searchFundRequests(
            List<String> locations,
            List<String> statuses,
            String receivedOn,
            String fromDate,
            String toDate,
            String search,
            String sortBy,
            String sortDirection) {

        CurrentUserService.LocationScope locationScope =
                currentUserService.resolveLocationScope(locations);

        if (locationScope.showsNothing()) {
            return List.of();
        }

        LocalDate[] range = resolveReceivedOnRange(receivedOn, fromDate, toDate);
        LocalDate from = range[0];
        LocalDate to = range[1];

        Set<String> wantedStatuses = normaliseStatusFilter(statuses);
        String needle = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase();

        List<UniversityScholarshipFundRequestListDto> matched = fundRequestRepository.findAll()
                .stream()
                // Scope is the parent's: a fund request has no location of its own.
                .filter(fundRequest -> {
                    UniversityScholarshipRequest parent = fundRequest.getUniversityScholarshipRequest();
                    return parent != null
                            && currentUserService.matchesScope(
                                    locationScope, parent.getSubmissionLocation());
                })
                .map(this::toFundRequestListDto)
                .filter(dto -> wantedStatuses.isEmpty()
                        || wantedStatuses.contains(canonicalStatus(dto.getStatus())))
                .filter(dto -> withinRange(dto.getRequestedDate(), from, to))
                .filter(dto -> matchesFundRequestSearch(dto, needle))
                .collect(Collectors.toCollection(ArrayList::new));

        matched.sort(fundRequestComparator(sortBy, sortDirection));
        return matched;
    }

    private UniversityScholarshipFundRequestListDto toFundRequestListDto(
            UniversityScholarshipFundRequest fundRequest) {

        UniversityScholarshipFundRequestListDto dto = new UniversityScholarshipFundRequestListDto();
        dto.setId(fundRequest.getId());
        dto.setRequestId(fundRequest.getFundRequestId());
        dto.setRequestedDate(fundRequest.getRequestedDate());
        dto.setRequestedPeriod(fundRequest.getRequestedPeriod());
        dto.setRequestedAmount(fundRequest.getRequestedAmount());
        dto.setDisbursedAmount(fundRequest.getDisbursedAmount());
        dto.setStatus(fundRequest.getStatus() != null ? fundRequest.getStatus().name() : "");

        UniversityScholarshipRequest parent = fundRequest.getUniversityScholarshipRequest();
        if (parent != null) {
            dto.setScholarshipRequestId(parent.getUniversityScholarshipRequestID());
            dto.setStudentName(parent.getStudentName());
            dto.setUniversityName(parent.getUniversity() != null ? parent.getUniversity().getName() : "");
            dto.setNic(parent.getNic());
            dto.setSubmissionLocation(parent.getSubmissionLocation());
            if (parent.getMember() != null) {
                dto.setMemberId(parent.getMember().getMemberId());
                dto.setMemberName(parent.getMember().getFullName());
            }
        }

        return dto;
    }

    /** Same fields the scholarship search covers, plus the fund request's own id. */
    private boolean matchesFundRequestSearch(
            UniversityScholarshipFundRequestListDto dto, String needle) {

        if (needle == null) {
            return true;
        }
        return containsIgnoreCase(dto.getRequestId(), needle)
                || containsIgnoreCase(dto.getScholarshipRequestId(), needle)
                || containsIgnoreCase(dto.getStudentName(), needle)
                || containsIgnoreCase(dto.getMemberName(), needle)
                || containsIgnoreCase(dto.getMemberId(), needle)
                || containsIgnoreCase(dto.getNic(), needle);
    }

    private Comparator<UniversityScholarshipFundRequestListDto> fundRequestComparator(
            String sortBy, String sortDirection) {

        String key = sortBy == null ? "requested-date" : sortBy.trim().toLowerCase();

        Comparator<UniversityScholarshipFundRequestListDto> comparator = switch (key) {
            case "status" -> Comparator.comparing(
                    UniversityScholarshipFundRequestListDto::getStatus,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "member-id", "memberid" -> Comparator.comparing(
                    UniversityScholarshipFundRequestListDto::getMemberId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "scholarship-id", "scholarshipid" -> Comparator.comparing(
                    UniversityScholarshipFundRequestListDto::getScholarshipRequestId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(
                    UniversityScholarshipFundRequestListDto::getRequestedDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };

        return "desc".equalsIgnoreCase(sortDirection) ? comparator.reversed() : comparator;
    }

    // Get scholarship request by request ID with member and university details
    public UniversityScholarshipListDto getScholarshipRequestByRequestId(String requestId) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

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
        dto.setFinanceIntegratedAt(request.getFinanceIntegratedAt());
        dto.setFinanceIntegratedBy(request.getFinanceIntegratedBy());
        return dto;
    }
    
    //hand an approved fund request to the Finance Module.
    public UniversityScholarshipFundRequestDto integrateFundRequestWithFinance(
            String scholarshipRequestId,
            String fundRequestId
    ) {
        UniversityScholarshipFundRequest fundRequest =
                findFundRequestForScholarship(scholarshipRequestId, fundRequestId);

        if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.APPROVED) {
            throw new RuntimeException(
                    "Only Approved Fund Requests can be integrated with the Finance Module");
        }

        if (fundRequest.getFinanceIntegratedAt() != null) {
            throw new RuntimeException(
                    "This Fund Request has already been integrated with the Finance Module");
        }

        com.memberconnect.backend.model.User currentUser = currentUserService.current();
        fundRequest.setFinanceIntegratedAt(LocalDateTime.now());
        fundRequest.setFinanceIntegratedBy(currentUser != null ? currentUser.getUsername() : null);

        return toFundRequestDto(fundRequestRepository.save(fundRequest));
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

        UniversityScholarshipFundRequest saved = fundRequestRepository.save(fundRequest);
        notifyFundRequestMember(
                saved, UniversityScholarshipFundRequestStatus.INCOMPLETE, reason.trim());

        return toFundRequestDto(saved);
    }

    //change a fund request's status from View Mode.
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

        if (newStatus == UniversityScholarshipFundRequestStatus.NEW) {
            fundRequest.setIncompleteReason(null);
            fundRequest.setDecisionReason(null);
        }

        return toFundRequestDto(fundRequestRepository.save(fundRequest));
    }

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

            UniversityScholarshipFundRequest saved = fundRequestRepository.save(fundRequest);
            notifyFundRequestMember(
                    saved, UniversityScholarshipFundRequestStatus.INCOMPLETE, reason.trim());

            return toFundRequestDto(saved);
        }

        if (fundRequest.getStatus() != UniversityScholarshipFundRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
            throw new RuntimeException("Only Submitted for Approval Fund Requests can be approved or rejected");
        }

        if (nextStatus == UniversityScholarshipFundRequestStatus.APPROVED) {
            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.APPROVED);
            fundRequest.setIncompleteReason(null);
            fundRequest.setDecisionReason(null);

            UniversityScholarshipFundRequest saved = fundRequestRepository.save(fundRequest);
            notifyFundRequestMember(
                    saved, UniversityScholarshipFundRequestStatus.APPROVED, null);

            return toFundRequestDto(saved);
        }

        if (nextStatus == UniversityScholarshipFundRequestStatus.REJECTED) {
            if (!StringUtils.hasText(reason)) {
                throw new RuntimeException("Rejection reason is required");
            }

            fundRequest.setStatus(UniversityScholarshipFundRequestStatus.REJECTED);
            fundRequest.setIncompleteReason(null);
            fundRequest.setDecisionReason(reason.trim());

            UniversityScholarshipFundRequest saved = fundRequestRepository.save(fundRequest);
            notifyFundRequestMember(
                    saved, UniversityScholarshipFundRequestStatus.REJECTED, reason.trim());

            return toFundRequestDto(saved);
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

        validateMemberActiveDuringExam(dto.getMemberId(), dto.getExamYear());
        validateMembershipDuration(dto);
        validateFinanceEligibility(dto, member);
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

        boolean followDeviation = Boolean.TRUE.equals(dto.getFollowDeviationProcess());

        int eligibilityMonths = scholarshipConfigRepository.findByConfigKey("scholarship.eligibility.period.months")
                .map(com.memberconnect.backend.model.ScholarshipConfig::getConfigValue)
                .orElse(6);

        try {
                UniversityScholarshipExamMaster examMaster = examMasterRepository
                        .findByExamYear(dto.getExamYear())
                        .orElse(null);

                if (dto.getRequestDate() != null && examMaster != null) {
                        LocalDate examLastDate = examMaster.getExamLastDate();
                       
                        LocalDate latestAllowed = examLastDate.plusMonths(eligibilityMonths);

                        LocalDate reqDate = dto.getRequestDate();
                        
                        if (reqDate.isBefore(examLastDate) || reqDate.isAfter(latestAllowed)) {
                                followDeviation = true;
                        }
                }
        } catch (Exception ignored) {

        }

        request.setFollowDeviationProcess(followDeviation);

        request.setSubmissionLocation(member.getSubmissionLocation());
        com.memberconnect.backend.model.User currentUser = currentUserService.current();
        request.setCreatedBy(currentUser != null ? currentUser.getUsername() : null);
        request.setCreatedAt(LocalDateTime.now());

        request.setStatus(UniversityScholarshipRequestStatus.NEW);
        
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

        // The exam year is editable here, so the rule has to hold on the way out too -
        // otherwise a request created against a valid exam could be switched to one the
        // member was not Active for
        validateMemberActiveDuringExam(
                        dto.getMemberId() != null
                                ? dto.getMemberId()
                                : (request.getMember() != null ? request.getMember().getMemberId() : null),
                        dto.getExamYear());

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

    public UniversityScholarshipRequest committeeApproveRequest(String requestId) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (request.getStatus() != UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
            throw new RuntimeException(
                    "Only requests submitted for Committee Approval can be approved by the Committee");
        }

        if (Boolean.TRUE.equals(request.getFollowDeviationProcess())) {
            request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL);
        } else {
            request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_BOARD_APPROVAL);
        }

        stampCommitteeDecision(request);

        return scholarshipRequestRepository.save(request);
    }

    public UniversityScholarshipRequest committeeRejectRequest(String requestId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("A reason is required to reject a scholarship request");
        }

        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
                
        if (request.getStatus() != UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
            throw new RuntimeException(
                    "Only requests submitted for Committee Approval can be rejected by the Committee");
        }

        request.setStatus(UniversityScholarshipRequestStatus.REJECTED);
        request.setRejectReason(reason.trim());
        stampCommitteeDecision(request);

        UniversityScholarshipRequest saved = scholarshipRequestRepository.save(request);

        notifyMember(saved, UniversityScholarshipRequestStatus.REJECTED, reason.trim());

        return saved;
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

                UniversityScholarshipRequest saved = scholarshipRequestRepository.save(request);

                notifyMember(saved, UniversityScholarshipRequestStatus.INCOMPLETE, reason);

                return saved;
    }

    /**
     * Emails the member about a decision on their request.
     *
     * Best-effort by design: NotificationService swallows its own delivery failures, and
     * this adds the same guard around a member that cannot be resolved, because an
     * undeliverable email must never undo a decision that has already been recorded.
     */
    private void notifyMember(
            UniversityScholarshipRequest request,
            UniversityScholarshipRequestStatus status,
            String reason
    ) {
        String memberId = request.getMember() != null ? request.getMember().getMemberId() : null;
        if (!StringUtils.hasText(memberId)) {
            return;
        }

        String requestNo = request.getUniversityScholarshipRequestID();
        String studentName = request.getStudentName();

        switch (status) {
            case INCOMPLETE -> notificationService.notifyUniversityScholarshipMarkedIncomplete(
                    memberId, requestNo, studentName, reason);
            case REJECTED -> notificationService.notifyUniversityScholarshipRejected(
                    memberId, requestNo, studentName, reason);
            case APPROVED -> notificationService.notifyUniversityScholarshipApproved(
                    memberId, requestNo, studentName);
            default -> { }
        }
    }

    /**
     * Emails the member about a decision on a fund request raised against their
     * scholarship. Best-effort, like notifyMember above.
     */
    private void notifyFundRequestMember(
            UniversityScholarshipFundRequest fundRequest,
            UniversityScholarshipFundRequestStatus status,
            String reason
    ) {
        UniversityScholarshipRequest scholarship = fundRequest.getUniversityScholarshipRequest();
        String memberId = scholarship != null && scholarship.getMember() != null
                ? scholarship.getMember().getMemberId()
                : null;

        if (!StringUtils.hasText(memberId)) {
            return;
        }

        String fundRequestNo = fundRequest.getFundRequestId();
        String scholarshipNo = scholarship.getUniversityScholarshipRequestID();
        String studentName = scholarship.getStudentName();
        String period = fundRequest.getRequestedPeriod();

        switch (status) {
            case INCOMPLETE -> notificationService.notifyFundRequestMarkedIncomplete(
                    memberId, fundRequestNo, scholarshipNo, studentName, period, reason);
            case REJECTED -> notificationService.notifyFundRequestRejected(
                    memberId, fundRequestNo, scholarshipNo, studentName, period, reason);
            case APPROVED -> notificationService.notifyFundRequestApproved(
                    memberId, fundRequestNo, scholarshipNo, studentName, period,
                    fundRequest.getRequestedAmount());
            default -> { }
        }
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

    // Deletes a Normal Approval List by rolling back all attached requests to
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

    
    //Deletes a Deviation Approval List by rolling back all attached requests to
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
    // Processes normal board approvals with file upload.
    @Transactional
    public void processApprovals(String dataJson, MultipartFile file) {
        processApprovalDecisions(dataJson, file, UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST);
    }

    // Processes deviation board approvals with file upload.
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
                    scannedReportPath = s3Service.uploadFile(file, S3Service.folder(
                            "university-scholarships", "approval-lists", approvalListId, "scanned-report"));
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

                if (request.getStatus() != expectedStatus) {
                    throw new RuntimeException("Request " + requestId + " is not awaiting this approval list"
                            + " (expected " + expectedStatus + ", found " + request.getStatus() + ")");
                }

                if (approvalListId != null && !approvalListId.equals(request.getApprovalListId())) {
                    throw new RuntimeException("Request " + requestId
                            + " does not belong to approval list " + approvalListId);
                }

                if ("reject".equalsIgnoreCase(action)) {
                    
                    if (!StringUtils.hasText(reason)) {
                        throw new RuntimeException("A rejection reason is required for request " + requestId);
                    }
                    request.setStatus(UniversityScholarshipRequestStatus.REJECTED);
                    request.setRejectReason(reason.trim());
                } else {
                    request.setStatus(UniversityScholarshipRequestStatus.APPROVED);
                }

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

                UniversityScholarshipRequest saved = scholarshipRequestRepository.save(request);

                // After the save, so the member is only told about a decision that was
                // actually recorded
                notifyMember(saved, saved.getStatus(), saved.getRejectReason());
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

    //change a request's status from View Mode.
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

        request.setStatus(newStatus);

        if (newStatus == UniversityScholarshipRequestStatus.NEW) {
            request.setIncompleteReason(null);
            request.setRejectReason(null);
        }

        return scholarshipRequestRepository.save(request);
    }

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
