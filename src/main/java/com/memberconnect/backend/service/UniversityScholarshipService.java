package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.ProgramOptionDto;
import com.memberconnect.backend.dto.UniversityScholarshipRequestDto;
import com.memberconnect.backend.enums.ApplicantType;
import com.memberconnect.backend.enums.UniversityScholarshipRequestStatus;
import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.model.MinorAccount;
import com.memberconnect.backend.model.Program;
import com.memberconnect.backend.model.University;
import com.memberconnect.backend.model.UniversityProgram;
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
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
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

import java.time.LocalDate;
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

    @Value("${scholarship.required.remitted.months}")
    private int requiredRemittedMonths;

    @Value("${scholarship.lookback.years}")
    private int lookbackYears;

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
            BoardmeetingRepository boardMeetingRepository
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
    public List<UniversityScholarshipListDto> getAllScholarshipRequests() {
        return scholarshipRequestRepository.findAll()
                .stream()
                .map(request -> {
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
                        request.getIncompleteReason() != null ? request.getIncompleteReason() : ""
                    );
                    if (request.getBoardMeeting() != null) {
                        dto.setBoardMeetingId(request.getBoardMeeting().getId());
                        dto.setBoardMeetingName(request.getBoardMeeting().getBoardMeetingId());
                    }
                    return dto;
                })
                .toList();
    }

    // Get scholarship request by request ID with member and university details
    public UniversityScholarshipListDto getScholarshipRequestByRequestId(String requestId) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                .findByUniversityScholarshipRequestID(requestId)
                .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

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
                request.getIncompleteReason() != null ? request.getIncompleteReason() : ""
        );
        if (request.getBoardMeeting() != null) {
            dto.setBoardMeetingId(request.getBoardMeeting().getId());
            dto.setBoardMeetingName(request.getBoardMeeting().getBoardMeetingId());
        }
        return dto;
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
                        LocalDate earliestAllowed = examLastDate.minusMonths(eligibilityMonths);

                        LocalDate reqDate = dto.getRequestDate();
                        if (reqDate.isBefore(earliestAllowed) || reqDate.isAfter(examLastDate)) {
                                followDeviation = true;
                        }
                }
        } catch (Exception ignored) {
        // If any config or exam master lookup fails, do not block save; leave followDeviation as provided
        }

        request.setFollowDeviationProcess(followDeviation);

        request.setUniversityScholarshipRequestID(generateUniversityScholarshipRequestID());

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

        request.setUniversityScholarshipRequestID(generateUniversityScholarshipRequestID());
        return scholarshipRequestRepository.save(request);
    }   

    public UniversityScholarshipRequest updateRequestByRequestId(String requestId, UniversityScholarshipRequestDto dto) {
        UniversityScholarshipRequest request = scholarshipRequestRepository
                        .findByUniversityScholarshipRequestID(requestId)
                        .orElseThrow(() -> new RuntimeException("Scholarship request not found"));

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

    // Approve request by committee and forward to appropriate board list, or approve from board list
    public UniversityScholarshipRequest approveRequest(String requestId) {
                 UniversityScholarshipRequest request = scholarshipRequestRepository
                         .findByUniversityScholarshipRequestID(requestId)
                         .orElseThrow(() -> new RuntimeException("Request not found"));

                 if (request.getStatus() == UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST ||
                     request.getStatus() == UniversityScholarshipRequestStatus.ADDED_TO_DEVIATION_BOARD_APPROVAL_LIST) {
                         request.setStatus(UniversityScholarshipRequestStatus.APPROVED);
                 } else if (request.getStatus() == UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL) {
                         if (Boolean.TRUE.equals(request.getFollowDeviationProcess())) {
                                 request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL);
                         } else {
                                 request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_BOARD_APPROVAL);
                         }
                 } else {
                         throw new RuntimeException("Only requests submitted for committee approval or added to board approval list can be approved");
                 }

                 return scholarshipRequestRepository.save(request);
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

        for (String requestId : requestIds) {
            UniversityScholarshipRequest request = scholarshipRequestRepository.findByUniversityScholarshipRequestID(requestId)
                    .orElseThrow(() -> new RuntimeException("Scholarship Request not found: " + requestId));
            request.setBoardMeeting(boardMeeting);
            request.setStatus(UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST);
            scholarshipRequestRepository.save(request);
        }
    }

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

        for (String requestId : requestIds) {
            UniversityScholarshipRequest request = scholarshipRequestRepository.findByUniversityScholarshipRequestID(requestId)
                    .orElseThrow(() -> new RuntimeException("Scholarship Request not found: " + requestId));
            request.setBoardMeeting(boardMeeting);
            request.setStatus(UniversityScholarshipRequestStatus.ADDED_TO_DEVIATION_BOARD_APPROVAL_LIST);
            scholarshipRequestRepository.save(request);
        }
    }

    /**
     * Deletes a Normal Approval List by rolling back all attached requests to
     * SUBMITTED_FOR_NORMAL_BOARD_APPROVAL and detaching them from the board meeting.
     */
    @Transactional
    public void deleteApprovalList(Long boardMeetingId) {
        BoardMeeting boardMeeting = boardMeetingRepository.findById(boardMeetingId)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found: " + boardMeetingId));

        List<UniversityScholarshipRequest> requests =
                scholarshipRequestRepository.findByBoardMeeting(boardMeeting);

        if (requests.isEmpty()) {
            throw new RuntimeException("No approval list found for Board Meeting #" + boardMeetingId);
        }

        for (UniversityScholarshipRequest request : requests) {
            if (request.getStatus() == UniversityScholarshipRequestStatus.ADDED_TO_NORMAL_BOARD_APPROVAL_LIST) {
                request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_BOARD_APPROVAL);
                request.setBoardMeeting(null);
                scholarshipRequestRepository.save(request);
            }
        }
    }

    /**
     * Deletes a Deviation Approval List by rolling back all attached requests to
     * SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL and detaching them from the board meeting.
     */
    @Transactional
    public void deleteDeviationApprovalList(Long boardMeetingId) {
        BoardMeeting boardMeeting = boardMeetingRepository.findById(boardMeetingId)
                .orElseThrow(() -> new RuntimeException("Board Meeting not found: " + boardMeetingId));

        List<UniversityScholarshipRequest> requests =
                scholarshipRequestRepository.findByBoardMeeting(boardMeeting);

        if (requests.isEmpty()) {
            throw new RuntimeException("No deviation approval list found for Board Meeting #" + boardMeetingId);
        }

        for (UniversityScholarshipRequest request : requests) {
            if (request.getStatus() == UniversityScholarshipRequestStatus.ADDED_TO_DEVIATION_BOARD_APPROVAL_LIST) {
                request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_BOARD_APPROVAL);
                request.setBoardMeeting(null);
                scholarshipRequestRepository.save(request);
            }
        }
    }
}