package com.memberconnect.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;

@Service
public class Grade5ScholarshipService {

    @Autowired
    private Grade5ScholarshipRepository repository;

    // ✅ Check exam number exists
    public boolean isExamNumberExists(String examNo) {
        return repository.existsByExaminationNumber(examNo);
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
       
        return repository.save(entity);
    }
}