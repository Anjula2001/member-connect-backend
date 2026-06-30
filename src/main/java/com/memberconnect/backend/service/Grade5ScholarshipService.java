package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.Grade5RequestListDTO;
import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.ScholarshipRequestStatus;
import com.memberconnect.backend.model.Grade5ExamMaster;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.repository.Grade5ExamMasterRepository;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;


@Service
public class Grade5ScholarshipService {

    @Autowired
    private Grade5ScholarshipRepository repository;
    @Autowired
    private MinorSavingsAccountRepository minorRepo;
    @Autowired
    private Grade5ExamMasterRepository Grade5ExamMasterRepository;
    @Autowired
    private MemberRepository memberRepository;

    // Check exam number exists
    public boolean isExamNumberExists(String examNo) {
        return repository.existsByExaminationNumber(examNo);
    }

    public List<MinorSavingsAccount> getMinorAccounts(String birthCertificateNo) {
        return minorRepo.findByBirthCertificateNo(birthCertificateNo);
    }

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

        if (member.getStatus() != MemberStatus.ACTIVE
                || member.getMembershipStartDate() == null
                || member.getMembershipStartDate().isAfter(examLastDate)) {
            throw new RuntimeException(
                    "The Grade 5 Scholarship Request cannot be saved. The Member is not Active during the selected Exam"
            );
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
    public Grade5ScholarshipRequest saveRequest(String memberId, Grade5StudentDTO dto)  {
        
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        validateMemberActiveOnExamLastDate(memberId, dto.getExamYear());

        // Prevent duplicate
        if (repository.existsByExaminationNumber(dto.getExaminationNumber())) {
            throw new RuntimeException("Examination number already exists");
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
        entity.setMinorAccountExists(dto.getMinorAccountExists());
        entity.setMinorAccountNumber(dto.getMinorAccountNumber());
        entity.setEligibleMonths(dto.getEligibleMonths());
        entity.setDisbursementOption(dto.getDisbursementOption());
        entity.setMemberAmount(dto.getMemberAmount());
        entity.setMinorAmount(dto.getMinorAmount());
        entity.setIsDoubleAmount(dto.getIsDoubleAmount());
        entity.setHasDeviation(shouldFollowDeviationProcess(requestedDate, dto.getExamYear()));
       
        List<MinorSavingsAccount> accounts =
                minorRepo.findByBirthCertificateNo(dto.getBirthCertificateNumber());

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
                            lastNo.substring(lastNo.lastIndexOf("-") + 1)
                    );

                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    // Get fund disbursement details
    public Map<String, Object> getFundDisbursementDetails(String birthCertificateNo) {

        List<MinorSavingsAccount> accounts =
                minorRepo.findByBirthCertificateNo(birthCertificateNo);

        Map<String, Object> result = new HashMap<>();

        boolean hasMinor = !accounts.isEmpty();

        result.put("hasMinorAccount", hasMinor);

        if (!hasMinor) {
            result.put("minorAccountNo", null);
            result.put("totalMonths", 0);
            result.put("eligibleMonths", 0);
            result.put("doubleAmount", false);
            return result;
        }

        MinorSavingsAccount account = accounts.get(0);

        result.put("minorAccountNo", account.getMinorAccountNo());

        int eligibleMonths = (int) accounts.stream()
                .filter(a -> a.getRemittedAmount() != null && a.getRemittedAmount() >= 250)
                .count();

        result.put("totalMonths", accounts.size());
        result.put("eligibleMonths", eligibleMonths);
        result.put("doubleAmount", eligibleMonths >= 36);

        return result;
    }

    // Get latest request for member
    public Grade5ScholarshipRequest getLatestRequest(String memberId) {
        return repository
            .findTopByMemberIdOrderByIdDesc(memberId)
            .orElse(null);
    }

    // Mark incomplete
    public Grade5ScholarshipRequest markIncomplete(String requestNo, String reason) {
        Grade5ScholarshipRequest request = repository.findByRequestNo(requestNo)
                .orElseThrow(() -> new RuntimeException("Grade 5 request not found"));

        request.setStatus("INCOMPLETE");
        request.setIncompleteReason(reason);

        return repository.save(request);
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
                || shouldFollowDeviationProcess(request.getRequestedDate(), request.getExamYear());

        String finalStatus = isDeviation
                ? ScholarshipRequestStatus.SUBMITTED_FOR_DEVIATION_APPROVAL.name()
                : ScholarshipRequestStatus.SUBMITTED_FOR_NORMAL_APPROVAL.name();

        request.setStatus(finalStatus);
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
            List<String> years,
            List<String> statuses,
            String receivedOn,
            String fromDate,
            String toDate,
            String search,
            String sortBy,
            String sortDirection
    ) {
        LocalDate today = LocalDate.now();

        return repository.findAll()
                .stream()
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
                                safeString(result2.getStatus())
                        );
                    } else if ("MEMBER_ID".equals(sortBy)) {
                        result = safeString(result1.getMemberId()).compareToIgnoreCase(
                                safeString(result2.getMemberId())
                        );
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
                            r.getDistrict(),
                            Boolean.TRUE.equals(r.getHasDeviation())
                
                    );
                })
                .toList();
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

        if (repository.existsByExaminationNumberAndRequestNoNot(
                dto.getExaminationNumber(),
                requestNo
        )) {
            throw new RuntimeException("Examination number already exists");
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

        entity.setMinorAccountExists(dto.getMinorAccountExists());
        entity.setMinorAccountNumber(dto.getMinorAccountNumber());
        entity.setEligibleMonths(dto.getEligibleMonths());
        entity.setDisbursementOption(dto.getDisbursementOption());
        entity.setMemberAmount(dto.getMemberAmount());
        entity.setMinorAmount(dto.getMinorAmount());
        entity.setIsDoubleAmount(dto.getIsDoubleAmount());
        entity.setHasDeviation(shouldFollowDeviationProcess(requestedDate, dto.getExamYear()));

        entity.setIncompleteReason(null);

        return repository.save(entity);
    }
    
}