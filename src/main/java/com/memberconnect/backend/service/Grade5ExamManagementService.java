package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.dto.DistrictCutoffDetailDTO;
import com.memberconnect.backend.dto.Grade5ExamManagementDTO;
import com.memberconnect.backend.model.DistrictCutoff;
import com.memberconnect.backend.model.EducationalDistrict;
import com.memberconnect.backend.model.Grade5ExamMaster;
import com.memberconnect.backend.repository.DistrictCutoffRepository;
import com.memberconnect.backend.repository.EducationalDistrictRepository;
import com.memberconnect.backend.repository.Grade5ExamMasterRepository;

@Service
public class Grade5ExamManagementService {

    private final Grade5ExamMasterRepository examMasterRepository;
    private final DistrictCutoffRepository cutoffRepository;
    private final EducationalDistrictRepository educationalDistrictRepository;

    public Grade5ExamManagementService(
            Grade5ExamMasterRepository examMasterRepository,
            DistrictCutoffRepository cutoffRepository,
            EducationalDistrictRepository educationalDistrictRepository
    ) {
        this.examMasterRepository = examMasterRepository;
        this.cutoffRepository = cutoffRepository;
        this.educationalDistrictRepository = educationalDistrictRepository;
    }

    public Grade5ExamManagementDTO getExamDetails(Integer examYear) {
        if (examYear == null) {
            examYear = LocalDate.now().getYear();
        }

        // Find the Grade5ExamMaster record
        Optional<Grade5ExamMaster> examMasterOpt = examMasterRepository.findById(examYear);
        LocalDate examDate = examMasterOpt.map(Grade5ExamMaster::getExamDate).orElse(null);

        // Get all educational districts
        List<EducationalDistrict> districts = educationalDistrictRepository.findAll();

        // Create cutoff list
        List<DistrictCutoffDetailDTO> cutoffs = new ArrayList<>();
        for (EducationalDistrict district : districts) {
            String districtName = district.getName();
            Optional<DistrictCutoff> cutoffOpt = cutoffRepository.findByDistrictAndExamYear(districtName, examYear);
            Integer cutoffMarks = cutoffOpt.map(DistrictCutoff::getCutoffMarks).orElse(null);

            cutoffs.add(new DistrictCutoffDetailDTO(districtName, cutoffMarks));
        }

        return new Grade5ExamManagementDTO(examYear, examDate, cutoffs);
    }

    @Transactional
    public void saveExamDetails(Grade5ExamManagementDTO dto) {
        int currentYear = LocalDate.now().getYear();
        Integer examYear = dto.getExamYear();
        LocalDate examDate = dto.getExamDate();

        // Validation:
        // 1. Exam year must be current year
        if (examYear == null) {
            throw new IllegalArgumentException("Exam year is required.");
        }
        if (examYear > currentYear) {
            throw new IllegalArgumentException("Exam year cannot be a future year.");
        }
        if (examYear < currentYear) {
            throw new IllegalArgumentException("Exam year cannot be a past year.");
        }

        // 2. Exam date is required and must belong to the selected year
        if (examDate == null) {
            throw new IllegalArgumentException("Exam date is required.");
        }
        if (examDate.getYear() != examYear) {
            throw new IllegalArgumentException("Exam date must belong to the selected year (" + examYear + ").");
        }

        // 3. Exam date cannot be a future date
        if (examDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Exam date cannot be a future date.");
        }

        // Save exam master details
        Grade5ExamMaster examMaster = examMasterRepository.findById(examYear)
                .orElse(new Grade5ExamMaster());
        examMaster.setYear(examYear);
        examMaster.setExamDate(examDate);
        examMasterRepository.save(examMaster);

        // Save district cutoff marks
        List<DistrictCutoffDetailDTO> cutoffs = dto.getCutoffs();
        if (cutoffs == null || cutoffs.isEmpty()) {
            throw new IllegalArgumentException("District cutoff marks are required.");
        }

        // Ensure all districts in master have a cutoff mark
        List<EducationalDistrict> districts = educationalDistrictRepository.findAll();
        if (cutoffs.size() < districts.size()) {
            throw new IllegalArgumentException("Cutoff marks must be entered for all districts.");
        }

        for (DistrictCutoffDetailDTO cutoffDetail : cutoffs) {
            String districtName = cutoffDetail.getDistrict();
            Integer cutoffMarks = cutoffDetail.getCutoffMarks();

            if (districtName == null || districtName.trim().isEmpty()) {
                throw new IllegalArgumentException("District name is required.");
            }
            if (cutoffMarks == null) {
                throw new IllegalArgumentException("Cutoff marks are required for district " + districtName + ".");
            }
            if (cutoffMarks < 0 || cutoffMarks > 200) {
                throw new IllegalArgumentException("Cutoff marks for " + districtName + " must be between 0 and 200.");
            }

            Optional<DistrictCutoff> existingCutoffOpt = cutoffRepository.findByDistrictAndExamYear(districtName, examYear);
            DistrictCutoff cutoff;
            if (existingCutoffOpt.isPresent()) {
                cutoff = existingCutoffOpt.get();
            } else {
                cutoff = new DistrictCutoff();
                cutoff.setDistrict(districtName);
                cutoff.setExamYear(examYear);
            }
            cutoff.setCutoffMarks(cutoffMarks);
            cutoffRepository.save(cutoff);
        }
    }
}
