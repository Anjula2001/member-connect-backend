package com.memberconnect.backend.service;
import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@Service
@Transactional
@SuppressWarnings("null")
public class MemberApplicationService {
    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private ModelMapper modelMapper;

    public MemberApplicationDTO saveMemberApplication(MemberApplicationDTO memberApplicationDTO) {
        Member_Application application = modelMapper.map(memberApplicationDTO, Member_Application.class);
        application.setApplicationID("APP-" + System.currentTimeMillis());
        Member_Application saved = memberApplicationRepository.save(application);
        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    public List<MemberApplicationDTO>getAllMemberApplications(){
        List<Member_Application>memberApplications = memberApplicationRepository.findAll();
        return modelMapper.map(memberApplications, new TypeToken<List<MemberApplicationDTO>>() {}.getType());
    }

    public MemberApplicationDTO updateMemberApplication(Long id, MemberApplicationDTO dto) {
        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        applyNonNullFields(existing, dto);
        Member_Application updated = memberApplicationRepository.save(existing);
        return modelMapper.map(updated, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO updatePartial(Long id, MemberApplicationDTO dto) {

        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        applyNonNullFields(existing, dto);

        Member_Application saved = memberApplicationRepository.save(existing);

        return modelMapper.map(saved, MemberApplicationDTO.class);
    }

    private void applyNonNullFields(Member_Application existing, MemberApplicationDTO dto) {
        if (dto.getApplicationDate() != null) existing.setApplicationDate(dto.getApplicationDate());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());

        if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
        if (dto.getFullName() != null) existing.setFullName(dto.getFullName());
        if (dto.getNameAsInPayroll() != null) existing.setNameAsInPayroll(dto.getNameAsInPayroll());
        if (dto.getNameWithInitials() != null) existing.setNameWithInitials(dto.getNameWithInitials());
        if (dto.getNicNumber() != null) existing.setNicNumber(dto.getNicNumber());

        if (dto.getDateOfBirth() != null) existing.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) existing.setGender(dto.getGender());
        if (dto.getPreferredLanguage() != null) existing.setPreferredLanguage(dto.getPreferredLanguage());

        if (dto.getPermanentPrivateAddress() != null) existing.setPermanentPrivateAddress(dto.getPermanentPrivateAddress());
        if (dto.getWorkingLocationType() != null) existing.setWorkingLocationType(dto.getWorkingLocationType());
        if (dto.getDesignation() != null) existing.setDesignation(dto.getDesignation());
        if (dto.getNatureOfOccupation() != null) existing.setNatureOfOccupation(dto.getNatureOfOccupation());
        if (dto.getEducationalDistrict() != null) existing.setEducationalDistrict(dto.getEducationalDistrict());
        if (dto.getEducationalZone() != null) existing.setEducationalZone(dto.getEducationalZone());
        if (dto.getWorkingLocation() != null) existing.setWorkingLocation(dto.getWorkingLocation());
        if (dto.getWorkingLocationAddress() != null) existing.setWorkingLocationAddress(dto.getWorkingLocationAddress());
        if (dto.getComputerNoInPayslip() != null) existing.setComputerNoInPayslip(dto.getComputerNoInPayslip());
        if (dto.getSalaryPayingOffice() != null) existing.setSalaryPayingOffice(dto.getSalaryPayingOffice());
        if (dto.getOfficeTelephone() != null) existing.setOfficeTelephone(dto.getOfficeTelephone());
        if (dto.getPrivateTelephone() != null) existing.setPrivateTelephone(dto.getPrivateTelephone());
        if (dto.getMobileNumber() != null) existing.setMobileNumber(dto.getMobileNumber());
        if (dto.getEmailAddress() != null) existing.setEmailAddress(dto.getEmailAddress());

        if (dto.getShareAccountAmount() != null) existing.setShareAccountAmount(dto.getShareAccountAmount());
        if (dto.getSpecialDepositAmount() != null) existing.setSpecialDepositAmount(dto.getSpecialDepositAmount());
        if (dto.getFixedDepositAmount() != null) existing.setFixedDepositAmount(dto.getFixedDepositAmount());
        if (dto.getScholarshipDeathDonationPensionAmount() != null)
            existing.setScholarshipDeathDonationPensionAmount(dto.getScholarshipDeathDonationPensionAmount());

        if (dto.getNomineeFullName() != null) existing.setNomineeFullName(dto.getNomineeFullName());
        if (dto.getNomineeRelationship() != null) existing.setNomineeRelationship(dto.getNomineeRelationship());
        if (dto.getIdentification() != null) existing.setIdentification(dto.getIdentification());
        if (dto.getIdentificationNumber() != null) existing.setIdentificationNumber(dto.getIdentificationNumber());
        if (dto.getIdentificationDetails() != null) existing.setIdentificationDetails(dto.getIdentificationDetails());
        if (dto.getNomineeAddress() != null) existing.setNomineeAddress(dto.getNomineeAddress());
        if (dto.getRejoinFlag() != null) existing.setRejoinFlag(dto.getRejoinFlag());
    }

    public String deleteMemberApplication(Long id) {

        if (!memberApplicationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        memberApplicationRepository.deleteById(id);

        return "Application deleted successfully";
    }

    public MemberApplicationDTO getApplicationById(Long id) {

        Member_Application application = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"
                ));

        return modelMapper.map(application, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO getApplicationByNic(String nic) {

        Member_Application application = memberApplicationRepository.findByNicNumber(nic)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"
                ));

        return modelMapper.map(application, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO updateStatus(Long id, ApplicationStatus status) {

        Member_Application app = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"));

        app.setStatus(status);

        return modelMapper.map(memberApplicationRepository.save(app), MemberApplicationDTO.class);
    }
}