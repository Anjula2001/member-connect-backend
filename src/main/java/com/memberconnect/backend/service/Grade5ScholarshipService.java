package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.event.Grade5MarkedIncompleteEvent;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.Grade5RequestListDTO;
import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.ScholarshipRequestStatus;
import com.memberconnect.backend.model.Grade5ExamMaster;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.Grade5ExamMasterRepository;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import com.memberconnect.backend.repository.MinorAccountRemittanceRepository;
import com.memberconnect.backend.repository.ScholarshipConfigRepository;
import com.memberconnect.backend.repository.ScholarshipRemittanceRepository;
import org.springframework.beans.factory.annotation.Value;

@Service
public class Grade5ScholarshipService {

    private static final Logger log = LoggerFactory.getLogger(Grade5ScholarshipService.class);

    @Autowired
    private Grade5ScholarshipRepository repository;
    @Autowired
    private MinorSavingsAccountRepository minorRepo;
    @Autowired
    private MinorAccountRemittanceRepository minorAccountRemittanceRepository;
    @Autowired
    private Grade5ExamMasterRepository Grade5ExamMasterRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ScholarshipRemittanceRepository remittanceRepository;
    @Autowired
    private ScholarshipConfigRepository scholarshipConfigRepository;
    @Autowired
    private com.memberconnect.backend.config.CurrentUserService currentUserService;
    @Autowired
    private MemberStatusHistoryService memberStatusHistoryService;

    @Value("${grade5.scholarship.required.remitted.months:6}")
    private int defaultRequiredRemittedMonths;

    @Value("${grade5.minor.account.required.remittance.amount:250.0}")
    private double defaultMinMinorRemittanceAmount;

    @Value("${grade5.scholarship.base.amount:5000}")
    private int defaultBaseScholarshipAmount;

    @Value("${grade5.scholarship.double.eligible.months:36}")
    private int defaultDoubleEligibleMonths;

    @Value("${grade5.scholarship.amount.multiplier:2}")
    private int defaultAmountMultiplier;

    // Check exam number exists
    public boolean isExamNumberExists(String examNo) {
        return repository.existsByExaminationNumber(examNo);
    }

    // Check birth certificate number exists. One birth certificate identifies one
    // student, so a second request carrying it is a duplicate of an existing one.
    public boolean isBirthCertificateNumberExists(String birthCertificateNo) {
        return repository.existsByBirthCertificateNumber(birthCertificateNo);
    }

    public List<MinorSavingsAccount> getMinorAccounts(String birthCertificateNo) {
        return minorRepo.findByBirthCertificateNo(birthCertificateNo);
    }

    /**
     * MMS02: the member must have been Active on the last date of the selected exam.
     *
     * member_status_history is the authority, not Member.status. A request can be
     * raised up to a year after the exam, and by then the member's current status says
     * nothing about where they stood on the day that actually decides eligibility - a
     * member who retired last month was still Active at the exam, and a member who is
     * Active again today may have been Inactive when their child sat it.
     *
     * History that is silent about the date is not evidence of inactivity: rows only
     * exist from the point the table was introduced. So the member's current status is
     * the fallback rather than an unknown passing unchecked - it is what this
     * validation used before the history table existed.
     */
    private void validateMemberActiveOnExamLastDate(String memberId, Integer examYear) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (examYear == null) {
            throw new RuntimeException("Selected exam year is required");
        }

        Grade5ExamMaster examMaster = Grade5ExamMasterRepository.findById(examYear)
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamDate();

        if (examLastDate == null) {
            throw new RuntimeException("Selected exam date not found in Exam Master");
        }

        MemberStatus recordedStatus = memberStatusHistoryService.statusOn(memberId, examLastDate);
        MemberStatus statusAtExam = recordedStatus != null ? recordedStatus : member.getStatus();

        // Joining after the exam is the same failure by a different route: there was no
        // membership to be Active in on the day that counts.
        boolean notYetAMemberAtExam = member.getMembershipStartDate() == null
                || member.getMembershipStartDate().isAfter(examLastDate);

        if (statusAtExam != MemberStatus.ACTIVE || notYetAMemberAtExam) {
            throw new RuntimeException(
                    "The Grade 5 Scholarship Request cannot be saved. The Member is not Active during the selected Exam");
        }
    }

    // Validate membership period for the selected exam year. The member must have been a member for at least 36 months as of the exam date.
    private void validateMembershipPeriodForExam(String memberId, Integer examYear) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (examYear == null) {
            throw new RuntimeException("Selected exam year is required");
        }

        Grade5ExamMaster examMaster = Grade5ExamMasterRepository.findById(examYear)
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamDate();

        if (examLastDate == null) {
            throw new RuntimeException("Selected exam date not found in Exam Master");
        }

        LocalDate membershipStartDate = member.getMembershipStartDate();
        if (membershipStartDate != null) {
            LocalDate start = membershipStartDate.withDayOfMonth(1);

            // Validate membership age today
            LocalDate today = LocalDate.now().withDayOfMonth(1);
            long monthsToday = java.time.temporal.ChronoUnit.MONTHS.between(start, today);
            if (monthsToday < 36) {
                throw new RuntimeException("The required continues Membership period does not comply (36 months)");
            }

            // Validate membership age as of the exam date
            LocalDate end = examLastDate.withDayOfMonth(1);
            long monthsExam = java.time.temporal.ChronoUnit.MONTHS.between(start, end);
            if (monthsExam < 36) {
                throw new RuntimeException("The required continues Membership period does not comply (36 months)");
            }
        }
    }

    // Validate scholarship remittance for the selected exam year. The member must have remitted the scholarship deduction continuously for the required number of months (default 6) as of the exam date.
    private void validateScholarshipRemittance(String memberId, Integer examYear) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (examYear == null) {
            throw new RuntimeException("Selected exam year is required");
        }

        Grade5ExamMaster examMaster = Grade5ExamMasterRepository.findById(examYear)
                .orElseThrow(() -> new RuntimeException("Selected exam year not found in Exam Master"));

        LocalDate examLastDate = examMaster.getExamDate();
        if (examLastDate == null) {
            throw new RuntimeException("Selected exam date not found in Exam Master");
        }

        int requiredMonths = scholarshipConfigRepository.findByConfigKey("grade5.scholarship.required.remitted.months")
                .or(() -> scholarshipConfigRepository.findByConfigKey("scholarship.required.remitted.months"))
                .map(com.memberconnect.backend.model.ScholarshipConfig::getConfigValue)
                .orElse(defaultRequiredRemittedMonths > 0 ? defaultRequiredRemittedMonths : 6);

        LocalDate examMonthDate = examLastDate.withDayOfMonth(1);
        System.out.println("Validating Scholarship Remittance for Member: " + memberId + ", Exam Year: " + examYear
                + ", Exam Date: " + examLastDate + ", Required Months: " + requiredMonths);

        for (int i = 1; i <= requiredMonths; i++) {
            LocalDate targetMonth = examMonthDate.minusMonths(i);
            String monthHyphen = String.format("%04d-%02d", targetMonth.getYear(), targetMonth.getMonthValue());
            String monthDot = String.format("%04d.%02d", targetMonth.getYear(), targetMonth.getMonthValue());

            boolean remitted = remittanceRepository.existsByMember_IdAndRemittanceMonthAndRemittedTrue(member.getId(),
                    monthHyphen)
                    || remittanceRepository.existsByMember_IdAndRemittanceMonthAndRemittedTrue(member.getId(), monthDot)
                    || remittanceRepository.existsByMember_MemberIdAndRemittanceMonthAndRemittedTrue(memberId,
                            monthHyphen)
                    || remittanceRepository.existsByMember_MemberIdAndRemittanceMonthAndRemittedTrue(memberId,
                            monthDot);

            System.out.println("Checking month " + i + " (" + monthHyphen + "): remitted=" + remitted);

            if (!remitted) {
                System.out.println("Remittance check FAILED for month: " + monthHyphen);
                throw new RuntimeException(
                        "Scholarship deduction was not continuously remitted from Member for the specific period ("
                                + requiredMonths + " months)");
            }
        }
    }

    private int configValue(String primaryKey, String fallbackKey, int fallbackValue) {
        return scholarshipConfigRepository.findByConfigKey(primaryKey)
                .or(() -> scholarshipConfigRepository.findByConfigKey(fallbackKey))
                .map(com.memberconnect.backend.model.ScholarshipConfig::getConfigValue)
                .filter(value -> value != null && value > 0)
                .orElse(fallbackValue);
    }

    /**
     * The total the student is entitled to. Rs. 5,000 normally; doubled once the minor
     * account has been remitted with the required amount for the configured number of
     * months (36 by default, and the months need not be consecutive).
     */
    private int expectedTotalAmount(int eligibleMonths) {
        int baseAmount = configValue(
                "grade5.scholarship.base.amount",
                "scholarship.base.amount",
                defaultBaseScholarshipAmount > 0 ? defaultBaseScholarshipAmount : 5000);

        int doubleAfterMonths = configValue(
                "grade5.scholarship.double.eligible.months",
                "scholarship.double.eligible.months",
                defaultDoubleEligibleMonths > 0 ? defaultDoubleEligibleMonths : 36);

        int multiplier = configValue(
                "grade5.scholarship.amount.multiplier",
                "scholarship.amount.multiplier",
                defaultAmountMultiplier > 0 ? defaultAmountMultiplier : 2);

        return eligibleMonths >= doubleAfterMonths ? baseAmount * multiplier : baseAmount;
    }

    
    private void validateFundDisbursement(Grade5StudentDTO dto) {
        if (dto.getEligibleMonths() != null && dto.getEligibleMonths() < 0) {
            throw new RuntimeException("Eligible months cannot be negative.");
        }

        String option = dto.getDisbursementOption();
        if (option == null || option.trim().isEmpty()) {
            throw new RuntimeException("Please select a fund disbursement option.");
        }

        boolean hasMinorAccount = Boolean.TRUE.equals(dto.getMinorAccountExists());

        if (!hasMinorAccount && !"MEMBER_ONLY".equalsIgnoreCase(option)) {
            throw new RuntimeException("Minor account disbursement options are not allowed without a minor account.");
        }

        if (hasMinorAccount && (dto.getMinorAccountNumber() == null || dto.getMinorAccountNumber().trim().isEmpty())) {
            throw new RuntimeException("Minor account number is required.");
        }

        if ("MINOR_ONLY".equalsIgnoreCase(option)
                && (dto.getMinorAccountNumber() == null || dto.getMinorAccountNumber().trim().isEmpty())) {
            throw new RuntimeException("Minor account number is required for Minor Account Only option.");
        }

        if ((dto.getMemberAmount() != null && dto.getMemberAmount() < 0)
                || (dto.getMinorAmount() != null && dto.getMinorAmount() < 0)) {
            throw new RuntimeException("Disbursement amounts cannot be negative.");
        }

        int eligibleMonths = dto.getEligibleMonths() != null ? dto.getEligibleMonths() : 0;

        // The eligible months are counted from minor account remittances, so there is no
        // way to have earned any without an account to remit into.
        if (!hasMinorAccount && eligibleMonths > 0) {
            throw new RuntimeException(
                    "Eligible months cannot be greater than zero without a minor account.");
        }

        int expectedTotal = expectedTotalAmount(eligibleMonths);
        Integer memberAmount = dto.getMemberAmount();
        Integer minorAmount = dto.getMinorAmount();

        // No minor account: the whole scholarship is paid to the member.
        if (!hasMinorAccount) {
            if (minorAmount != null && minorAmount != 0) {
                throw new RuntimeException(
                        "Without a minor account the minor account amount must be zero.");
            }

            if (memberAmount != null && memberAmount != expectedTotal) {
                throw new RuntimeException(
                        "Without a minor account the full scholarship amount of Rs. "
                                + expectedTotal + " must be paid to the member.");
            }
        }

        // Whatever the split, the two halves must still add up to what the student earned.
        if (memberAmount != null && minorAmount != null
                && memberAmount + minorAmount != expectedTotal) {
            throw new RuntimeException(
                    "Member and minor account amounts must add up to the total scholarship amount of Rs. "
                            + expectedTotal + ".");
        }
    }

    private boolean shouldFollowDeviationProcess(LocalDate requestedDate, Integer examYear) {
        if (requestedDate == null || examYear == null) {
            return false;
        }

        Grade5ExamMaster examMaster = Grade5ExamMasterRepository.findById(examYear).orElse(null);
        if (examMaster == null || examMaster.getExamDate() == null) {
            return false;
        }

        LocalDate examDate = examMaster.getExamDate();
        LocalDate latestAllowed = examDate.plusYears(1);

        return requestedDate.isBefore(examDate) || requestedDate.isAfter(latestAllowed);
    }

    public Map<String, Object> computeDeviationInfo(LocalDate requestedDate, Integer examYear) {
        Map<String, Object> result = new HashMap<>();

        result.put("deviation", false);

        if (requestedDate == null || examYear == null) {
            return result;
        }

        Grade5ExamMaster examMaster = Grade5ExamMasterRepository.findById(examYear).orElse(null);
        if (examMaster == null || examMaster.getExamDate() == null) {
            return result;
        }

        LocalDate examDate = examMaster.getExamDate();
        LocalDate latestAllowed = examDate.plusYears(1);

        boolean deviation = requestedDate.isBefore(examDate) || requestedDate.isAfter(latestAllowed);

        result.put("deviation", deviation);
        result.put("examDate", examDate.toString());
        result.put("latestAllowed", latestAllowed.toString());

        return result;
    }

    // Save request
    public Grade5ScholarshipRequest saveRequest(String memberId, Grade5StudentDTO dto) {

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        validateMemberActiveOnExamLastDate(memberId, dto.getExamYear());
        validateMembershipPeriodForExam(memberId, dto.getExamYear());
        validateScholarshipRemittance(memberId, dto.getExamYear());
        validateFundDisbursement(dto);

        // Prevent duplicate
        if (repository.existsByExaminationNumber(dto.getExaminationNumber())) {
            throw new RuntimeException("Examination number already exists");
        }

        if (repository.existsByBirthCertificateNumber(dto.getBirthCertificateNumber())) {
            throw new RuntimeException("Birth certificate number already exists");
        }

        Grade5ScholarshipRequest entity = new Grade5ScholarshipRequest();
        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());

        entity.setRequestNo(generateRequestNo());
        entity.setRequestedDate(requestedDate);
        entity.setMemberId(memberId);
        entity.setStatus("NEW");
        entity.setStudentName(dto.getStudentName());
        entity.setExaminationNumber(dto.getExaminationNumber());
        entity.setExamYear(dto.getExamYear());
        entity.setMarksObtained(dto.getMarksObtained());
        entity.setBirthCertificateNumber(dto.getBirthCertificateNumber());
        entity.setSchool(dto.getStudentSchool());
        entity.setDistrict(dto.getSchoolDistrict());
        entity.setDistrictCutOffMark(dto.getDistrictCutOffMark());
        Map<String, Object> calc = calculateDisbursement(
                dto.getEligibleMonths(),
                dto.getDisbursementOption(),
                dto.getMinorAccountExists());

        entity.setMinorAccountExists(dto.getMinorAccountExists());
        entity.setMinorAccountNumber(dto.getMinorAccountNumber());
        entity.setEligibleMonths(dto.getEligibleMonths());
        entity.setDisbursementOption((String) calc.get("disbursementOption"));
        entity.setMemberAmount((Integer) calc.get("memberAmount"));
        entity.setMinorAmount((Integer) calc.get("minorAmount"));
        entity.setIsDoubleAmount((Boolean) calc.get("isDoubleAmount"));
        entity.setHasDeviation(shouldFollowDeviationProcess(requestedDate, dto.getExamYear()));

        User currentUser = currentUserService.current();

        // The request is booked to the district of the office that raised it. Accounts
        // with no district of their own (Super Admin, Scholarship Officer) fall back to
        // the member's administering office, so the column is never left null — the
        // location filter on the search screen matches on it.
        String raisingDistrict = currentUser != null ? currentUser.getAssignedDistrict() : null;
        entity.setSubmissionLocation(
                (raisingDistrict != null && !raisingDistrict.isBlank())
                        ? raisingDistrict.trim()
                        : member.getSubmissionLocation());

        entity.setCreatedBy(currentUser != null ? currentUser.getUsername() : null);
        entity.setCreatedAt(java.time.LocalDateTime.now());

        List<MinorSavingsAccount> accounts = minorRepo.findByBirthCertificateNo(dto.getBirthCertificateNumber());

        boolean hasMinorAccount = !accounts.isEmpty();

        if (hasMinorAccount) {
            System.out.println("Minor account found");
        } else {
            System.out.println("No minor account found");
        }

        return repository.save(entity);
    }

    // Generate request number
    private String generateRequestNo() {
        int year = LocalDate.now().getYear();
        String prefix = "G5-" + year + "-";

        return repository.findTopByRequestNoStartingWithOrderByRequestNoDesc(prefix)
                .map(lastRequest -> {
                    String lastNo = lastRequest.getRequestNo();

                    int lastSeq = Integer.parseInt(
                            lastNo.substring(lastNo.lastIndexOf("-") + 1));

                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    // Get fund disbursement details
    public Map<String, Object> getFundDisbursementDetails(String birthCertificateNo, Integer examYear) {

        List<MinorSavingsAccount> accounts = minorRepo.findByBirthCertificateNo(birthCertificateNo);

        Map<String, Object> result = new HashMap<>();

        boolean hasMinor = !accounts.isEmpty();

        result.put("hasMinorAccount", hasMinor);

        if (!hasMinor) {
            result.put("minorAccountNo", null);
            result.put("totalMonths", 0);
            result.put("eligibleMonths", 0);

            result.putAll(calculateDisbursement(0, "MEMBER_ONLY", false));
            return result;
        }

        MinorSavingsAccount account = accounts.get(0);

        result.put("minorAccountNo", account.getMinorAccountNo());

        // Determine the last month of the exam if examYear is available
        String examMonthLimit = null;
        if (examYear != null) {
            Grade5ExamMaster examMaster = Grade5ExamMasterRepository.findById(examYear).orElse(null);
            if (examMaster != null && examMaster.getExamDate() != null) {
                examMonthLimit = examMaster.getExamDate()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            }
        }

        List<com.memberconnect.backend.model.MinorAccountRemittance> remittances = minorAccountRemittanceRepository
                .findByMinorAccountNo(account.getMinorAccountNo());

        final String targetExamMonth = examMonthLimit;

        double minAmount = scholarshipConfigRepository
                .findByConfigKey("grade5.minor.account.required.remittance.amount")
                .or(() -> scholarshipConfigRepository.findByConfigKey("minor.account.required.remittance.amount"))
                .map(com.memberconnect.backend.model.ScholarshipConfig::getConfigValue)
                .map(Double::valueOf)
                .orElse(defaultMinMinorRemittanceAmount);

        int eligibleMonths = (int) remittances.stream()
                .filter(r -> r.getRemittanceAmount() != null && r.getRemittanceAmount() >= minAmount)
                .filter(r -> {
                    if (targetExamMonth == null)
                        return true;
                    String month = r.getRemittanceMonth();
                    if (month == null)
                        return false;
                    String normMonth = month.replace(".", "-");
                    String normTarget = targetExamMonth.replace(".", "-");
                    return normMonth.compareTo(normTarget) <= 0;
                })
                .count();

        result.put("totalMonths", eligibleMonths);
        result.put("eligibleMonths", eligibleMonths);

        String defaultOption = hasMinor ? "MEMBER_AND_MINOR" : "MEMBER_ONLY";
        Map<String, Object> calc = calculateDisbursement(eligibleMonths, defaultOption, hasMinor);
        result.putAll(calc);

        return result;
    }

    // Calculate fund disbursement breakdown
    public Map<String, Object> calculateDisbursement(Integer months, String option, Boolean hasMinorAccount) {
        int eligibleMonths = (months != null && months >= 0) ? months : 0;
        int totalAmount = expectedTotalAmount(eligibleMonths);
        boolean isDouble = totalAmount > expectedTotalAmount(0);

        boolean minorExists = Boolean.TRUE.equals(hasMinorAccount);
        String resolvedOption = minorExists ? (option != null && !option.trim().isEmpty() ? option : "MEMBER_AND_MINOR")
                : "MEMBER_ONLY";

        int memberAmount = 0;
        int minorAmount = 0;

        if ("MEMBER_ONLY".equalsIgnoreCase(resolvedOption)) {
            memberAmount = totalAmount;
            minorAmount = 0;
        } else if ("MEMBER_AND_MINOR".equalsIgnoreCase(resolvedOption)) {
            // An odd configured total would otherwise lose a rupee to integer division.
            minorAmount = totalAmount / 2;
            memberAmount = totalAmount - minorAmount;
        } else if ("MINOR_ONLY".equalsIgnoreCase(resolvedOption)) {
            memberAmount = 0;
            minorAmount = totalAmount;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("isDoubleAmount", isDouble);
        result.put("doubleAmount", isDouble);
        result.put("totalAmount", totalAmount);
        result.put("memberAmount", memberAmount);
        result.put("minorAmount", minorAmount);
        result.put("disbursementOption", resolvedOption);

        return result;
    }

    public Map<String, Object> getFundDisbursementDetails(String birthCertificateNo) {
        return getFundDisbursementDetails(birthCertificateNo, null);
    }

    // Deviation reason text, to be stamped onto a request that took the deviation route. It is not
    private static final String DEVIATION_REASON =
            "This request follows the deviation process. Because The Scholarship Request Date "
                    + "is not within the defined eligibility period from the last exam date.";

    // Stamp the deviation reason onto a request that took the deviation route. It is not
    private Grade5ScholarshipRequest withDeviationReason(Grade5ScholarshipRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getHasDeviation())) {
            return request;
        }

        request.setDeviationReason(DEVIATION_REASON);
        return request;
    }

    /** Every Grade 5 request held by a member, newest first. */
    public List<Grade5ScholarshipRequest> getRequestsForMember(String memberId) {
        return repository.findByMemberIdOrderByIdDesc(memberId)
                .stream()
                .map(this::withDeviationReason)
                .toList();
    }

    // Get latest request for member
    public Grade5ScholarshipRequest getLatestRequest(String memberId) {
        return repository
                .findTopByMemberIdOrderByIdDesc(memberId)
                .map(this::withDeviationReason)
                .orElse(null);
    }

    // Get a specific request by requestNo
    public java.util.Optional<Grade5ScholarshipRequest> getRequestByRequestNo(String requestNo) {
        return repository.findByRequestNo(requestNo).map(this::withDeviationReason);
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // Mark request as incomplete
    @Transactional
    public Grade5ScholarshipRequest markIncomplete(String requestNo, String reason) {
        Grade5ScholarshipRequest request = repository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Grade 5 request not found"));

        request.setStatus("INCOMPLETE");
        request.setIncompleteReason(reason);

        Grade5ScholarshipRequest saved = repository.save(request);

        eventPublisher.publishEvent(new Grade5MarkedIncompleteEvent(
                saved.getMemberId(),
                saved.getRequestNo(),
                saved.getStudentName(),
                reason
        ));

        return saved;
    }

    // Submit request
    public Grade5ScholarshipRequest submitRequest(String requestNo, String status) {

        Grade5ScholarshipRequest request = repository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Grade 5 request not found"));

        if (!"NEW".equals(request.getStatus()) && !"INCOMPLETE".equals(request.getStatus())) {
            throw new RuntimeException("Only NEW or INCOMPLETE requests can be submitted");
        }

        // Decide final submit status on the server to avoid relying on client-provided values.
        boolean isDeviation = Boolean.TRUE.equals(request.getHasDeviation())
                || shouldFollowDeviationProcess(request.getRequestedDate(), request.getExamYear())
                || (status != null && status.toUpperCase().contains("DEVIATION"));

        String finalStatus = isDeviation
                ? ScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_APPROVAL.name()
                : ScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_APPROVAL.name();

        request.setStatus(finalStatus);
        request.setHasDeviation(isDeviation);
        request.setIncompleteReason(null);

        return repository.save(request);
    }

    // Get exam years
    public List<Integer> getExamYears() {
        return Grade5ExamMasterRepository.findAll()
                .stream()
                .map(Grade5ExamMaster::getYear)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    // Search requests with filters
    public List<Grade5RequestListDTO> searchRequests(
            List<String> locations,
            List<String> years,
            List<String> statuses,
            String receivedOn,
            String fromDate,
            String toDate,
            String search,
            String sortBy,
            String sortDirection) {
        LocalDate today = LocalDate.now();

        
        CurrentUserService.LocationScope locationScope =
                currentUserService.resolveLocationScope(locations);

        
        if (locationScope.showsNothing()) {
            return List.of();
        }

        return repository.findAll()
                .stream()
                .filter(r -> currentUserService.matchesScope(locationScope, r.getSubmissionLocation()))
                .filter(r -> years == null || years.isEmpty()
                        || years.contains(String.valueOf(r.getExamYear())))
                .filter(r -> statuses == null || statuses.isEmpty()
                        || statuses.contains(r.getStatus()))
                .filter(r -> {
                    if (r.getRequestedDate() == null) {
                        return false;
                    }

                    LocalDate date = r.getRequestedDate();

                    if ("THIS_MONTH".equals(receivedOn)) {
                        return date.getMonth() == today.getMonth()
                                && date.getYear() == today.getYear();
                    }

                    if ("THIS_AND_LAST_MONTH".equals(receivedOn)) {
                        LocalDate start = today.minusMonths(1).withDayOfMonth(1);
                        return !date.isBefore(start) && !date.isAfter(today);
                    }

                    if ("DATE_PERIOD".equals(receivedOn)) {
                        if (fromDate == null || toDate == null
                                || fromDate.isBlank() || toDate.isBlank()) {
                            return false;
                        }

                        LocalDate from = LocalDate.parse(fromDate);
                        LocalDate to = LocalDate.parse(toDate);

                        return !date.isBefore(from) && !date.isAfter(to);
                    }

                    return true;
                })
                .filter(r -> {
                    if (search == null || search.isBlank()) {
                        return true;
                    }

                    String key = search.toLowerCase();

                    Member member = memberRepository.findByMemberId(r.getMemberId())
                            .orElse(null);

                    return contains(r.getMemberId(), key)
                            || contains(r.getStudentName(), key)
                            || contains(r.getExaminationNumber(), key)
                            || contains(r.getBirthCertificateNumber(), key)
                            || (member != null && contains(member.getFullName(), key))
                            || (member != null && contains(member.getNameAsInPayroll(), key))
                            || (member != null && contains(member.getNameWithInitials(), key))
                            || (member != null && contains(member.getMemberId(), key))
                            || (member != null && contains(member.getNic(), key));
                })
                .sorted((result1, result2) -> {
                    int result;

                    if ("STATUS".equals(sortBy)) {
                        result = safeString(result1.getStatus()).compareToIgnoreCase(
                                safeString(result2.getStatus()));
                    } else if ("MEMBER_ID".equals(sortBy)) {
                        result = safeString(result1.getMemberId()).compareToIgnoreCase(
                                safeString(result2.getMemberId()));
                    } else {
                        LocalDate dateA = result1.getRequestedDate();
                        LocalDate dateB = result2.getRequestedDate();

                        if (dateA == null && dateB == null) {
                            result = 0;
                        } else if (dateA == null) {
                            result = 1;
                        } else if (dateB == null) {
                            result = -1;
                        } else {
                            result = dateA.compareTo(dateB);
                        }
                    }

                    return "DESC".equals(sortDirection) ? -result : result;
                })
                .map(r -> {
                    Member member = memberRepository.findByMemberId(r.getMemberId())
                            .orElse(null);

                    return new Grade5RequestListDTO(
                            r.getId(),
                            r.getRequestNo(),
                            r.getMemberId(),
                            member != null ? member.getFullName() : "",
                            member != null ? member.getNameWithInitials() : "",
                            member != null ? member.getNic() : "",
                            r.getRequestedDate() != null ? r.getRequestedDate().toString() : null,
                            r.getStudentName(),
                            r.getExaminationNumber(),
                            r.getExamYear(),
                            r.getStatus(),
                            // The Location column means the District Office that owns the
                            // request, not the student's school district — those are
                            // different concepts and were previously conflated here.
                            r.getSubmissionLocation(),
                            Boolean.TRUE.equals(r.getHasDeviation())

                );
                })
                .toList();
    }

    /**
     * Works out which locations a search may actually cover.
     *
     * A pinned user (District Office) can never widen beyond their own branch: if
     * they
     * ask for other locations the request is narrowed back to theirs rather than
     * rejected, so a stale filter in the UI degrades to "your own district" instead
     * of
     * an error. An unpinned user gets exactly what they asked for, and "ALL" or an
     * empty selection means no location filtering at all.
     */
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

    /**
     * Rows saved before the location column existed carry a null location. They
     * stay
     * visible to everyone rather than disappearing, so enabling location scoping
     * does
     * not hide historical requests that nobody can re-tag.
     */
    private boolean matchesLocation(String requestLocation, List<String> locations) {
        if (locations.isEmpty()) {
            return true;
        }
        if (requestLocation == null || requestLocation.isBlank()) {
            return true;
        }
        return locations.stream().anyMatch(location -> location.equalsIgnoreCase(requestLocation));
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase().contains(key);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    // Update request
    public Grade5ScholarshipRequest updateRequest(String requestNo, Grade5StudentDTO dto) {
        Grade5ScholarshipRequest entity = repository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Grade 5 request not found"));

        validateMemberActiveOnExamLastDate(entity.getMemberId(), dto.getExamYear());
        validateMembershipPeriodForExam(entity.getMemberId(), dto.getExamYear());
        validateScholarshipRemittance(entity.getMemberId(), dto.getExamYear());
        validateFundDisbursement(dto);

        if (repository.existsByExaminationNumberAndRequestNoNot(
                dto.getExaminationNumber(),
                requestNo)) {
            throw new RuntimeException("Examination number already exists");
        }

        if (repository.existsByBirthCertificateNumberAndRequestNoNot(
                dto.getBirthCertificateNumber(),
                requestNo)) {
            throw new RuntimeException("Birth certificate number already exists");
        }

        LocalDate requestedDate = LocalDate.parse(dto.getRequestedDate());

        entity.setRequestedDate(requestedDate);
        entity.setStudentName(dto.getStudentName());
        entity.setExaminationNumber(dto.getExaminationNumber());
        entity.setExamYear(dto.getExamYear());
        entity.setMarksObtained(dto.getMarksObtained());
        entity.setBirthCertificateNumber(dto.getBirthCertificateNumber());
        entity.setSchool(dto.getStudentSchool());
        entity.setDistrict(dto.getSchoolDistrict());
        entity.setDistrictCutOffMark(dto.getDistrictCutOffMark());

        Map<String, Object> calc = calculateDisbursement(
                dto.getEligibleMonths(),
                dto.getDisbursementOption(),
                dto.getMinorAccountExists());

        entity.setMinorAccountExists(dto.getMinorAccountExists());
        entity.setMinorAccountNumber(dto.getMinorAccountNumber());
        entity.setEligibleMonths(dto.getEligibleMonths());
        entity.setDisbursementOption((String) calc.get("disbursementOption"));
        entity.setMemberAmount((Integer) calc.get("memberAmount"));
        entity.setMinorAmount((Integer) calc.get("minorAmount"));
        entity.setIsDoubleAmount((Boolean) calc.get("isDoubleAmount"));
        entity.setHasDeviation(shouldFollowDeviationProcess(requestedDate, dto.getExamYear()));

        
        if (!"INCOMPLETE".equals(entity.getStatus())) {
            entity.setIncompleteReason(null);
        }

        return repository.save(entity);
    }

    // Change Grade 5 Scholarship request status (view mode)
    public Grade5ScholarshipRequest changeRequestStatus(String requestNo, String newStatusStr) {
        Grade5ScholarshipRequest request = repository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Grade 5 Scholarship request not found"));

        ScholarshipRequestStatus newStatus;
        try {
            newStatus = ScholarshipRequestStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid status: " + newStatusStr);
        }

        ScholarshipRequestStatus currentStatus;
        try {
            currentStatus = ScholarshipRequestStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Current request status is unrecognized: " + request.getStatus());
        }

        if (currentStatus == newStatus) {
            return request;
        }

        if (!isGrade5StatusTransitionAllowed(currentStatus, newStatus)) {
            throw new RuntimeException(
                    "Cannot change status from " + currentStatus + " to " + newStatus);
        }

        
        if (newStatus == ScholarshipRequestStatus.NEW
                && request.getApprovalListId() != null
                && !request.getApprovalListId().isBlank()) {
            throw new RuntimeException(
                    "This request is attached to approval list " + request.getApprovalListId()
                            + " and cannot be returned to New. Remove it from the list first.");
        }

        request.setStatus(newStatus.name());

        if (newStatus == ScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_APPROVAL
                || newStatus == ScholarshipRequestStatus.ADDED_TO_SCHOLARSHIP_DEVIATION_APPROVAL_LIST) {
            request.setHasDeviation(true);
        } else if (newStatus == ScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_APPROVAL
                || newStatus == ScholarshipRequestStatus.ADDED_TO_SCHOLARSHIP_NORMAL_APPROVAL_LIST) {
            request.setHasDeviation(false);
        }

        // Clear reasons when returning to New
        if (newStatus == ScholarshipRequestStatus.NEW) {
            request.setIncompleteReason(null);
        }

        return repository.save(request);
    }

    // Check if the status transition is allowed for Grade 5 Scholarship requests
    private boolean isGrade5StatusTransitionAllowed(ScholarshipRequestStatus current, ScholarshipRequestStatus next) {
        switch (current) {
            case NEW:
                return next == ScholarshipRequestStatus.INACTIVE;
            case INCOMPLETE:
                return next == ScholarshipRequestStatus.NEW || next == ScholarshipRequestStatus.INACTIVE;
            case SUBMITTED_FOR_NORMAL_APPROVAL:
                return next == ScholarshipRequestStatus.NEW || next == ScholarshipRequestStatus.INACTIVE;
            case SUBMITTED_FOR_DEVIATION_APPROVAL:
                return next == ScholarshipRequestStatus.NEW || next == ScholarshipRequestStatus.INACTIVE;
            case REJECTED:
                return next == ScholarshipRequestStatus.INACTIVE || next == ScholarshipRequestStatus.NEW;
            case INACTIVE:
                return next == ScholarshipRequestStatus.NEW;
            default:
                return false;
        }
    }


    // send to Finance Module only for approved requests
    public Grade5ScholarshipRequest sendToFinanceModule(String requestNo) {
        Grade5ScholarshipRequest request = repository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException(
                        "Grade 5 Scholarship Request not found: " + requestNo));

        if (!ScholarshipRequestStatus.APPROVED.name().equalsIgnoreCase(request.getStatus())) {
            throw new RuntimeException(
                    "Only an approved Grade 5 Scholarship request can be sent to the Finance Module. "
                            + requestNo + " is " + request.getStatus() + ".");
        }

        callFinanceModuleApi(request);

        // "Once the funds are disbursed for the Grade 5 Scholarship, the record will
        // be made Inactive."
        request.setStatus(ScholarshipRequestStatus.INACTIVE.name());
        return repository.save(request);
    }

    // Mock API call to Finance Module
    private void callFinanceModuleApi(Grade5ScholarshipRequest request) {
        User sender = currentUserService != null ? currentUserService.current() : null;

        log.info("[FINANCE MODULE - MOCK API] Grade 5 scholarship handed off: requestNo={} memberId={} "
                + "studentName={} examYear={} disbursementOption={} memberAmount={} minorAmount={} "
                + "minorAccountNumber={} approvalListId={} location={} sentBy={}",
                request.getRequestNo(),
                request.getMemberId(),
                request.getStudentName(),
                request.getExamYear(),
                request.getDisbursementOption(),
                request.getMemberAmount(),
                request.getMinorAmount(),
                request.getMinorAccountNumber(),
                request.getApprovalListId(),
                request.getSubmissionLocation(),
                sender != null ? sender.getUsername() : null);
    }
}
