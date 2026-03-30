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

import java.util.List;

@Service
@Transactional
public class MemberApplicationService {
    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private ModelMapper modelMapper;

    public MemberApplicationDTO saveMemberApplication(MemberApplicationDTO memberApplicationDTO) {
        Member_Application application = modelMapper.map(memberApplicationDTO, Member_Application.class);
        application.setStatus(ApplicationStatus.PENDING);
        application.setApplicationID("APP-" + System.currentTimeMillis());
        memberApplicationRepository.save(application);
        return memberApplicationDTO;
    }

    public List<MemberApplicationDTO>getAllMemberApplications(){
        List<Member_Application>memberApplications = memberApplicationRepository.findAll();
        return modelMapper.map(memberApplications, new TypeToken<List<MemberApplicationDTO>>() {}.getType());
    }

    public MemberApplicationDTO updateMemberApplication(Long id, MemberApplicationDTO dto) {
        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        modelMapper.map(dto, existing);
        Member_Application updated = memberApplicationRepository.save(existing);
        return modelMapper.map(updated, MemberApplicationDTO.class);
    }

    public MemberApplicationDTO updatePartial(Long id, MemberApplicationDTO dto) {

        Member_Application existing = memberApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
        if (dto.getFullName() != null) existing.setFullName(dto.getFullName());
        if (dto.getNameAsInPayroll() != null) existing.setNameAsInPayroll(dto.getNameAsInPayroll());
        if (dto.getNameWithInitials() != null) existing.setNameWithInitials(dto.getNameWithInitials());
        if (dto.getNicNumber() != null) existing.setNicNumber(dto.getNicNumber());

        if (dto.getDateOfBirth() != null) existing.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) existing.setGender(dto.getGender());
        if (dto.getPreferredLanguage() != null) existing.setPreferredLanguage(dto.getPreferredLanguage());

        if (dto.getPermanentPrivateAddress() != null) existing.setPermanentPrivateAddress(dto.getPermanentPrivateAddress());
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

        if (dto.getRejoinFlag() != null) existing.setRejoinFlag(dto.getRejoinFlag());

        Member_Application saved = memberApplicationRepository.save(existing);

        return modelMapper.map(saved, MemberApplicationDTO.class);
    }
}