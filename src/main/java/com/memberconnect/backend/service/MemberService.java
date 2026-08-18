package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.MemberDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.MemberApplicationRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
@Service
@Transactional
@SuppressWarnings("null")
public class MemberService {
    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private NotificationService notificationService;

    public MemberDTO saveMember(MemberDTO memberDTO) {
        if (memberRepository.findByNic(memberDTO.getNic()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NIC already exists");
        }
        Member member = modelMapper.map(memberDTO, Member.class);
        member.setMemberId(generateMemberId());

        // Link the originating application if applicationId was supplied
        if (memberDTO.getApplicationId() != null) {
            Member_Application application = memberApplicationRepository
                    .findById(memberDTO.getApplicationId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Member application not found with id: " + memberDTO.getApplicationId()));
            member.setApplication(application);
        }

        Member saved = memberRepository.save(member);
        return convertToDTO(saved);
    }

    // Generates a sequential, year-scoped ID such as "MEM-2026-001", matching the
    // convention already used elsewhere in the app (e.g. RetirementService.generateRequestNo()).
    private String generateMemberId() {
        int year = LocalDate.now().getYear();
        String prefix = "MEM-" + year + "-";

        return memberRepository
                .findFirstByMemberIdStartingWithOrderByMemberIdDesc(prefix)
                .map(last -> {
                    String lastId = last.getMemberId();
                    int lastSeq = Integer.parseInt(lastId.substring(lastId.lastIndexOf("-") + 1));
                    return prefix + String.format("%03d", lastSeq + 1);
                })
                .orElse(prefix + "001");
    }

    public List<MemberDTO> getAllMembers() {
        List<Member> members = memberRepository.findAll();
        return members.stream()
                .map(this::convertToDTO)
                .toList();
    }

    public MemberDTO getMemberById(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return convertToDTO(member);
    }

    public MemberDTO getMemberByMemberId(String memberId) {
        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found with memberId: " + memberId));
        return convertToDTO(member);
    }

    public MemberDTO getMemberByNic(String nic) {
        Member member = memberRepository.findByNic(nic)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found with NIC: " + nic));
        return convertToDTO(member);
    }

    public List<MemberDTO> searchMembers(String query, List<MemberStatus> statuses, List<String> locations,
                                          String workingLocationType, String educationalZone) {

        // Normalise sentinel values from the UI
        final String q = (query == null || query.isBlank()) ? null : query.toLowerCase().trim();
        final boolean filterByStatus = (statuses != null && !statuses.isEmpty());
        final boolean filterByLocation = (locations != null && !locations.isEmpty());
        final String wlt = (workingLocationType == null || workingLocationType.isBlank()
                || "all-types".equalsIgnoreCase(workingLocationType)) ? null : workingLocationType.toLowerCase();
        final String ez = (educationalZone == null || educationalZone.isBlank()
                || "all-zones".equalsIgnoreCase(educationalZone)) ? null : educationalZone.toLowerCase();

        return memberRepository.findAll().stream()
                .filter(m -> {
                    // keyword search across name / NIC / memberId
                    if (q != null) {
                        boolean matches =
                                (m.getFullName() != null && m.getFullName().toLowerCase().contains(q)) ||
                                (m.getNameWithInitials() != null && m.getNameWithInitials().toLowerCase().contains(q)) ||
                                (m.getNic() != null && m.getNic().toLowerCase().contains(q)) ||
                                (m.getMemberId() != null && m.getMemberId().toLowerCase().contains(q));
                        if (!matches) return false;
                    }
                    // status filter
                    if (filterByStatus && !statuses.contains(m.getStatus())) return false;
                    // location filter — the District Office branch the member registered through,
                    // NOT the working location (school/institution name). These are different concepts:
                    // a member can work in one district and have registered via a District Office in another.
                    if (filterByLocation &&
                            (m.getSubmissionLocation() == null || !locations.contains(m.getSubmissionLocation())))
                        return false;
                    // working location type filter
                    if (wlt != null &&
                            (m.getWorkingLocationType() == null || !wlt.equalsIgnoreCase(m.getWorkingLocationType())))
                        return false;
                    // educational zone filter
                    if (ez != null &&
                            (m.getEducationalZone() == null || !ez.equalsIgnoreCase(m.getEducationalZone())))
                        return false;
                    return true;
                })
                .map(this::convertToDTO)
                .toList();
    }

    public MemberDTO updateMember(Long id, MemberDTO dto) {
        Member existing = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        applyNonNullFields(existing, dto);
        Member updated = memberRepository.save(existing);
        return convertToDTO(updated);
    }

    public String deleteMember(Long id) {

        if (!memberRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }

        memberRepository.deleteById(id);

        return "Member deleted successfully";
    }

    private void applyNonNullFields(Member existing, MemberDTO dto) {
        if (dto.getMemberType() != null) existing.setMemberType(dto.getMemberType());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        if (dto.getMembershipStartDate() != null) existing.setMembershipStartDate(dto.getMembershipStartDate());
        if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
        if (dto.getFullName() != null) existing.setFullName(dto.getFullName());
        if (dto.getNameAsInPayroll() != null) existing.setNameAsInPayroll(dto.getNameAsInPayroll());
        if (dto.getNameWithInitials() != null) existing.setNameWithInitials(dto.getNameWithInitials());
        if (dto.getDateOfBirth() != null) existing.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) existing.setGender(dto.getGender());
        if (dto.getPreferredLanguage() != null) existing.setPreferredLanguage(dto.getPreferredLanguage());
        if (dto.getPermanentPrivateAddress() != null) existing.setPermanentPrivateAddress(dto.getPermanentPrivateAddress());
        if (dto.getPrivateTelephone() != null) existing.setPrivateTelephone(dto.getPrivateTelephone());
        if (dto.getMobileNumber() != null) existing.setMobileNumber(dto.getMobileNumber());
        if (dto.getEmailAddress() != null) existing.setEmailAddress(dto.getEmailAddress());
        if (dto.getComputerNoInPayslip() != null) existing.setComputerNoInPayslip(dto.getComputerNoInPayslip());
        if (dto.getSalaryPayingOffice() != null) existing.setSalaryPayingOffice(dto.getSalaryPayingOffice());
        if (dto.getProfilePictureUrl() != null) existing.setProfilePictureUrl(dto.getProfilePictureUrl());
        if (dto.getSignatureUrl() != null) existing.setSignatureUrl(dto.getSignatureUrl());
        if (dto.getWorkingLocationType() != null) existing.setWorkingLocationType(dto.getWorkingLocationType());
        if (dto.getDesignation() != null) existing.setDesignation(dto.getDesignation());
        if (dto.getNatureOfOccupation() != null) existing.setNatureOfOccupation(dto.getNatureOfOccupation());
        if (dto.getEducationalDistrict() != null) existing.setEducationalDistrict(dto.getEducationalDistrict());
        if (dto.getEducationalZone() != null) existing.setEducationalZone(dto.getEducationalZone());
        if (dto.getWorkingLocation() != null) existing.setWorkingLocation(dto.getWorkingLocation());
        if (dto.getWorkingLocationAddress() != null) existing.setWorkingLocationAddress(dto.getWorkingLocationAddress());
        if (dto.getOfficeTelephone() != null) existing.setOfficeTelephone(dto.getOfficeTelephone());
        if (dto.getNomineeFullName() != null) existing.setNomineeFullName(dto.getNomineeFullName());
        if (dto.getNomineeRelationship() != null) existing.setNomineeRelationship(dto.getNomineeRelationship());
        if (dto.getNomineeAddress() != null) existing.setNomineeAddress(dto.getNomineeAddress());
        if (dto.getIdentification() != null) existing.setIdentification(dto.getIdentification());
        if (dto.getIdentificationNumber() != null) existing.setIdentificationNumber(dto.getIdentificationNumber());
        if (dto.getIdentificationDetails() != null) existing.setIdentificationDetails(dto.getIdentificationDetails());

    }

    public MemberDTO updateStatus(Long id, MemberStatus status) {

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Member not found"));

        // Real activation is supposed to come from the Finance Module once the member's
        // accounts are created there (out of scope for this build). Until that
        // integration exists, allow Super Admin only to flip a member to ACTIVE, as a
        // clearly testing-only stand-in — never treat this as the real activation path.
        if (status == MemberStatus.ACTIVE && !currentUserIsSuperAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Activating a member is a testing-only override reserved for Super Admin " +
                            "until the Finance Module integration exists."
            );
        }

        boolean isActivating = status == MemberStatus.ACTIVE && member.getStatus() != MemberStatus.ACTIVE;

        member.setStatus(status);
        Member saved = memberRepository.save(member);

        // MR12 — notify the member once their membership becomes active. Best-effort:
        // NotificationService swallows delivery failures so activation still succeeds.
        if (isActivating) {
            notificationService.sendMembershipActivated(saved);
        }

        return convertToDTO(saved);
    }

    private boolean currentUserIsSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof User user
                && user.getRole() == Role.SUPER_ADMIN;
    }

    private MemberDTO convertToDTO(Member member) {
        MemberDTO dto = modelMapper.map(member, MemberDTO.class);
        if (member.getApplication() != null) {
            dto.setApplicationId(member.getApplication().getId());
        }
        return dto;
    }
}