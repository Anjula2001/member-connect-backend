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

    // Method to get all member transfer requests
    public List<MemberTransferRequest> getAllRequests() {
        return memberTransferRepository.findAll();
    }

    // Method to get a specific member transfer request by ID
    public MemberTransferRequest getRequestById(Long id) {
        return memberTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member transfer request not found"));
    }

    /**
     * A member may only have one transfer request awaiting approval. A second one
     * would compete with the first: whichever is approved last overwrites the
     * member's working location with its own snapshot-era values.
     *
     * Only SUBMITTEDFORAPPROVAL blocks. An APPROVED transfer is finished work, so
     * the member can be transferred again later; REJECTED and INACTIVE never block.
     */
    private void assertNoRequestAwaitingApproval(Member member) {
        if (member == null || member.getMemberId() == null) {
            return;
        }

        memberTransferRepository
                .findFirstByMember_MemberIdAndStatus(
                        member.getMemberId(), MemberTransferStatus.SUBMITTEDFORAPPROVAL)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Member " + member.getMemberId() + " already has transfer request "
                                    + existing.getRequestId() + " awaiting approval."
                                    + " That request must be approved or rejected first.");
                });
    }

    // Returns the request awaiting approval for this member, or null when there is none
    public MemberTransferRequest findRequestAwaitingApproval(String memberId) {
        if (!StringUtils.hasText(memberId)) {
            return null;
        }

        return memberTransferRepository
                .findFirstByMember_MemberIdAndStatus(
                        memberId, MemberTransferStatus.SUBMITTEDFORAPPROVAL)
                .orElse(null);
    }

    // Method to submit a new member transfer request
    public MemberTransferRequest submitRequest(MemberTransferDto dto) {
        MemberTransferRequest request = new MemberTransferRequest();

        applyDtoToEntity(dto, request);

        assertNoRequestAwaitingApproval(request.getMember());

        // Snapshot the member's current values at time of request creation
        if (request.getMember() != null) {
            com.memberconnect.backend.model.Member m = request.getMember();
            request.setCurrentDesignation(m.getDesignation());
            request.setCurrentNatureOfOccupation(
                    m.getNatureOfOccupation() != null ? m.getNatureOfOccupation().name() : null);
            request.setCurrentWorkingLocationType(m.getWorkingLocationType());
            request.setCurrentEducationalDistrict(m.getEducationalDistrict());
            request.setCurrentEducationalZone(m.getEducationalZone());
            request.setCurrentWorkingLocation(m.getWorkingLocation());
            request.setCurrentWorkingLocationAddress(m.getWorkingLocationAddress());
            request.setCurrentComputerNoInPayslip(m.getComputerNoInPayslip());
            request.setCurrentSalaryPayingOffice(m.getSalaryPayingOffice());
        }

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

    //Generates a unique request ID 
    private String generateMemberTransferRequestId() {
        long nextNumber = memberTransferRepository.count() + 1;
        String candidate = String.format("MTR-%03d", nextNumber);

        while (memberTransferRepository.existsByRequestId(candidate)) {
            nextNumber++;
            candidate = String.format("MTR-%03d", nextNumber);
        }

        return candidate;
    }

    public MemberTransferRequest findRequestByIdOrRequestId(String key) {
        // Try finding by requestId first
        java.util.Optional<MemberTransferRequest> request = memberTransferRepository.findByRequestId(key);
        if (request.isPresent()) {
            return request.get();
        }
        // Try parsing as Long database ID
        try {
            Long id = Long.parseLong(key);
            return memberTransferRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Member transfer request not found with ID: " + key));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Member transfer request not found: " + key);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public MemberTransferRequest approveRequest(String key) {
        MemberTransferRequest request = findRequestByIdOrRequestId(key);

        if (request.getStatus() == MemberTransferStatus.APPROVED) {
            throw new RuntimeException("Request is already approved");
        }

        // Update request status
        request.setStatus(MemberTransferStatus.APPROVED);

        // Update Member profile with requested changes
        Member member = request.getMember();
        if (member != null) {
            if (request.getNewDesignation() != null) {
                member.setDesignation(request.getNewDesignation().getName());
            }
            if (request.getNewNatureOfOccupation() != null) {
                String name = request.getNewNatureOfOccupation().getName().toUpperCase();
                try {
                    member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.valueOf(name));
                } catch (IllegalArgumentException e) {
                    if (name.startsWith("PERM")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.PERMANENT);
                    } else if (name.startsWith("PROB")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.PROBATION);
                    } else if (name.startsWith("TEMP")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.TEMPORARY);
                    } else if (name.startsWith("CASU")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.CASUAL);
                    }
                }
            }
            if (request.getNewWorkingLocationType() != null) {
                member.setWorkingLocationType(request.getNewWorkingLocationType().getName());
            }
            if (request.getNewEducationalDistrict() != null) {
                member.setEducationalDistrict(request.getNewEducationalDistrict().getName());
            }
            if (request.getNewEducationalZone() != null) {
                member.setEducationalZone(request.getNewEducationalZone().getName());
            }
            if (request.getNewWorkingLocation() != null) {
                member.setWorkingLocation(request.getNewWorkingLocation().getName());
            }
            if (request.getNewWorkingLocationAddress() != null) {
                member.setWorkingLocationAddress(request.getNewWorkingLocationAddress());
            }
            if (request.getNewComputerNoInPayslip() != null) {
                member.setComputerNoInPayslip(request.getNewComputerNoInPayslip());
            }
            if (request.getNewSalaryPayingOffice() != null) {
                member.setSalaryPayingOffice(request.getNewSalaryPayingOffice());
            }
            memberRepository.save(member);
        }

        return memberTransferRepository.save(request);
    }

    @org.springframework.transaction.annotation.Transactional
    public MemberTransferRequest rejectRequest(String key, String reason) {
        MemberTransferRequest request = findRequestByIdOrRequestId(key);

        request.setStatus(MemberTransferStatus.REJECTED);
        request.setDecisionReason(reason);

        return memberTransferRepository.save(request);
    }
}