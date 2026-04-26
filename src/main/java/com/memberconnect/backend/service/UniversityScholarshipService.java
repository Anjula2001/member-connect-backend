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
import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.MinorAccountRepository;
import com.memberconnect.backend.repository.ProgramRepository;
import com.memberconnect.backend.repository.UniversityProgramRepository;
import com.memberconnect.backend.repository.UniversityRepository;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;
import org.springframework.stereotype.Service;

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

    public UniversityScholarshipService(
            UniversityRepository universityRepository,
            ProgramRepository programRepository,
            UniversityProgramRepository universityProgramRepository,
            UniversityScholarshipRequestRepository scholarshipRequestRepository,
            BankRepository bankRepository,
            BranchRepository branchRepository,
            MinorAccountRepository minorAccountRepository

    ) {
        this.universityRepository = universityRepository;
        this.programRepository = programRepository;
        this.universityProgramRepository = universityProgramRepository;
        this.scholarshipRequestRepository = scholarshipRequestRepository;
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
        this.minorAccountRepository = minorAccountRepository;
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
        }

        request.setUniversityScholarshipRequestID(generateUniversityScholarshipRequestID());
        return scholarshipRequestRepository.save(request);
    }   
 
    // Submit university scholarship request
    public UniversityScholarshipRequest submitRequest(Long id) {

        UniversityScholarshipRequest request = scholarshipRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Check required fields
        if (request.getStudentName() == null || request.getExamNo() == null) {
            throw new RuntimeException("Fill all required fields before submitting");
        }

        // Check documents uploaded
        boolean hasAllDocuments = checkDocumentsUploaded(id);

        if (!hasAllDocuments) {
            throw new RuntimeException("Upload all mandatory documents before submitting");
        }

        // Update status
        request.setStatus(UniversityScholarshipRequestStatus.SUBMITTED_FOR_COMMITTEE_APPROVAL);

        return scholarshipRequestRepository.save(request);
    }
    
    private boolean checkDocumentsUploaded(Long requestId) {
        return true; // temporary
    }

}