package com.memberconnect.backend.service;

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
    public Grade5ScholarshipRequest saveRequest(Grade5StudentDTO dto) {

        // Prevent duplicate
        if (repository.existsByExaminationNumber(dto.getExaminationNumber())) {
            throw new RuntimeException("Examination number already exists");
        }

        

        Grade5ScholarshipRequest entity = new Grade5ScholarshipRequest();

        entity.setStudentName(dto.getStudentName());
        entity.setExaminationNumber(dto.getExaminationNumber());
        entity.setExamYear(dto.getExamYear());
        entity.setMarksObtained(dto.getMarksObtained());
        entity.setBirthCertificateNumber(dto.getBirthCertificateNumber());
        entity.setSchool(dto.getStudentSchool());
        entity.setDistrict(dto.getSchoolDistrict());
       
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
    
}