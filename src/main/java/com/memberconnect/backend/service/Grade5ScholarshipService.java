package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;

@Service
public class Grade5ScholarshipService {

    @Autowired
    private Grade5ScholarshipRepository repository;

    @Autowired
    private MinorSavingsAccountRepository minorRepo;

    // ✅ Check exam number exists
    public boolean isExamNumberExists(String examNo) {
        return repository.existsByExaminationNumber(examNo);
    }

    public List<MinorSavingsAccount> getMinorAccounts(String birthCertificateNo) {
        return minorRepo.findByBirthCertificateNo(birthCertificateNo);
    }

    // Save request
    public Grade5ScholarshipRequest saveRequest(String memberId, Grade5StudentDTO dto)  {

        // Prevent duplicate
        if (repository.existsByExaminationNumber(dto.getExaminationNumber())) {
            throw new RuntimeException("Examination number already exists");
        }

        Grade5ScholarshipRequest entity = new Grade5ScholarshipRequest();
        
        entity.setRequestNo(generateRequestNo());
        entity.setRequestedDate(LocalDate.parse(dto.getRequestedDate()));
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

    public Grade5ScholarshipRequest getLatestRequest(String memberId) {
        return repository
            .findTopByMemberIdOrderByIdDesc(memberId)
            .orElse(null);
    }

    public Grade5ScholarshipRequest markIncomplete(Long requestId, String reason) {
        Grade5ScholarshipRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Grade 5 request not found"));

        request.setStatus("INCOMPLETE");
        request.setIncompleteReason(reason);

        return repository.save(request);
    }
    
}