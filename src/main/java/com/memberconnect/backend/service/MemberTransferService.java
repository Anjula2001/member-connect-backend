package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.MemberTransferDto;
import com.memberconnect.backend.enums.MemberTransferStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.model.WorkingLocation;
import com.memberconnect.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MemberTransferService {

    private final MemberTransferRepository memberTransferRepository;
    private final MemberRepository memberRepository;
    private final WorkingLocationTypeRepository workingLocationTypeRepository;
    private final EducationalDistrictRepository educationalDistrictRepository;
    private final EducationalZoneRepository educationalZoneRepository;
    private final WorkingLocationRepository workingLocationRepository;
    private final DesignationRepository designationRepository;
    private final NatureOfOccupationRepository natureOfOccupationRepository;

    public MemberTransferService(
            MemberTransferRepository memberTransferRepository,
            MemberRepository memberRepository,
            WorkingLocationTypeRepository workingLocationTypeRepository,
            EducationalDistrictRepository educationalDistrictRepository,
            EducationalZoneRepository educationalZoneRepository,
            WorkingLocationRepository workingLocationRepository,
            DesignationRepository designationRepository,
            NatureOfOccupationRepository natureOfOccupationRepository) {
        this.memberTransferRepository = memberTransferRepository;
        this.memberRepository = memberRepository;
        this.workingLocationTypeRepository = workingLocationTypeRepository;
        this.educationalDistrictRepository = educationalDistrictRepository;
        this.educationalZoneRepository = educationalZoneRepository;
        this.workingLocationRepository = workingLocationRepository;
        this.designationRepository = designationRepository;
        this.natureOfOccupationRepository = natureOfOccupationRepository;
    }

    public List<MemberTransferRequest> getAllRequests() {
        return memberTransferRepository.findAll();
    }

    public MemberTransferRequest getRequestById(Long id) {
        return memberTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member transfer request not found"));
    }

    public MemberTransferRequest submitRequest(MemberTransferDto dto) {
        MemberTransferRequest request = new MemberTransferRequest();

        applyDtoToEntity(dto, request);

        request.setRequestId(generateMemberTransferRequestId());
        request.setStatus(MemberTransferStatus.SUBMITTEDFORAPPROVAL);

        return memberTransferRepository.save(request);
    }

    public void deleteRequest(Long id) {
        memberTransferRepository.deleteById(id);
    }

    private void applyDtoToEntity(MemberTransferDto dto, MemberTransferRequest request) {

        if (dto.getMemberId() != null) {
            Member member = memberRepository.findById(Long.valueOf(dto.getMemberId()))
                    .orElseThrow(() -> new RuntimeException("Member not found"));
            request.setMember(member);
        }

        if (dto.getRequestedDate() != null) {
            request.setRequestedDate(dto.getRequestedDate());
        }

        if (dto.getNewWorkingLocationTypeId() != null) {
            request.setNewWorkingLocationType(
                    workingLocationTypeRepository.findById(dto.getNewWorkingLocationTypeId())
                            .orElseThrow(() -> new RuntimeException("Working location type not found")));
        }

        if (dto.getNewEducationalDistrictId() != null) {
            request.setNewEducationalDistrict(
                    educationalDistrictRepository.findById(dto.getNewEducationalDistrictId())
                            .orElseThrow(() -> new RuntimeException("Educational district not found")));
        }

        if (dto.getNewEducationalZoneId() != null) {
            request.setNewEducationalZone(
                    educationalZoneRepository.findById(dto.getNewEducationalZoneId())
                            .orElseThrow(() -> new RuntimeException("Educational zone not found")));
        }

        if (dto.getNewWorkingLocationId() != null) {
            WorkingLocation workingLocation = workingLocationRepository.findById(dto.getNewWorkingLocationId())
                    .orElseThrow(() -> new RuntimeException("Working location not found"));

            request.setNewWorkingLocation(workingLocation);

            request.setNewWorkingLocationAddress(workingLocation.getAddress());
            request.setNewSalaryPayingOffice(workingLocation.getSalaryPayingOffice());
        }

        if (dto.getNewDesignationId() != null) {
            request.setNewDesignation(
                    designationRepository.findById(dto.getNewDesignationId())
                            .orElseThrow(() -> new RuntimeException("Designation not found")));
        }

        if (dto.getNewNatureOfOccupationId() != null) {
            request.setNewNatureOfOccupation(
                    natureOfOccupationRepository.findById(dto.getNewNatureOfOccupationId())
                            .orElseThrow(() -> new RuntimeException("Nature of occupation not found")));
        }

        if (StringUtils.hasText(dto.getNewComputerNoInPayslip())) {
            request.setNewComputerNoInPayslip(dto.getNewComputerNoInPayslip().trim());
        }
    }

    private String generateMemberTransferRequestId() {
        long nextNumber = memberTransferRepository.count() + 1;
        String candidate = String.format("MTR-%03d", nextNumber);

        while (memberTransferRepository.existsByRequestId(candidate)) {
            nextNumber++;
            candidate = String.format("MTR-%03d", nextNumber);
        }

        return candidate;
    }
}