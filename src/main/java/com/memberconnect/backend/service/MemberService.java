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

import com.memberconnect.backend.enums.MembershipDocumentType;
import com.memberconnect.backend.repository.BoardApprovalListRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
@Service
@Transactional
@SuppressWarnings("null")
public class MemberService {

    /**
     * Member statuses owned by the termination workflow. See updateStatus() for why
     * they are refused on the generic status endpoint.
     */
    private static final java.util.Set<MemberStatus> TERMINATION_WORKFLOW_STATUSES =
            java.util.Set.of(
                    MemberStatus.TERMINATION_REQUESTED,
                    MemberStatus.TERMINATION_APPROVED,
                    MemberStatus.TERMINATED
            );

    @Autowired
    private MemberRepository memberRepository;

    // MMD10: a profile edit is one of the in-app proxies for account activity.
    // See MemberActivityService for why these are proxies and what the real
    // signal is.
    @Autowired
    private MemberActivityService memberActivityService;

    @Autowired
    private MemberApplicationRepository memberApplicationRepository;

    // MR15/16/17's Board Meeting Date filter. A member carries no meeting date, so the
    // period has to be resolved through the application the member was created from.
    @Autowired
    private BoardApprovalListRepository boardApprovalListRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MemberStatusHistoryService memberStatusHistoryService;

    @Autowired
    private MemberFinancialsService memberFinancialsService;

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
        auditService.record(AuditService.MODULE_MEMBER, saved.getId(),
                "Member Record Created", null, saved.getMemberId(),
                "Created from approved application");
        // Carry the application's remittance amounts onto the member. Without this
        // the amounts collected at registration are lost on approval, since Member
        // has no remittance columns of its own.
        memberFinancialsService.seedFromApplication(saved, saved.getApplication());
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

    /**
     * @param withoutDocument when set, keeps only members whose copy of that document
     *        has not been printed - the "Members without Membership Cards / Signature
     *        Cards / Passbooks" option the print screens default to (MR15/16/17). It
     *        used to be applied in the browser, which meant every active member was
     *        fetched and most were discarded, and the discarded share grows as more
     *        documents get printed.
     * @param sortBy one of memberID, status, working-location-type, district, zone;
     *        anything else (including null) sorts by membership date, the spec default.
     * @param sortDirection "desc" to reverse; ascending otherwise, per the spec default.
     */
    public List<MemberDTO> searchMembers(String query, List<MemberStatus> statuses, List<String> locations,
                                          String workingLocationType, String educationalZone,
                                          String educationalDistrict,
                                          LocalDate membershipStartFrom, LocalDate membershipStartTo,
                                          MembershipDocumentType withoutDocument,
                                          LocalDate boardMeetingFrom, LocalDate boardMeetingTo,
                                          String sortBy, String sortDirection) {

        // Normalise sentinel values from the UI
        final String q = (query == null || query.isBlank()) ? null : query.toLowerCase().trim();
        final boolean filterByStatus = (statuses != null && !statuses.isEmpty());
        final boolean filterByLocation = (locations != null && !locations.isEmpty());
        final String wlt = (workingLocationType == null || workingLocationType.isBlank()
                || "all-types".equalsIgnoreCase(workingLocationType)) ? null : workingLocationType.toLowerCase();
        final String ez = (educationalZone == null || educationalZone.isBlank()
                || "all-zones".equalsIgnoreCase(educationalZone)) ? null : educationalZone.toLowerCase();
        final String ed = (educationalDistrict == null || educationalDistrict.isBlank()
                || "all-districts".equalsIgnoreCase(educationalDistrict)) ? null : educationalDistrict.toLowerCase();

        // Resolved once rather than per member. Null means "Any" - the spec default -
        // and is distinct from an empty set, which means a period was given and no
        // meeting in it approved anybody.
        final Set<Long> approvedApplicationIds =
                (boardMeetingFrom == null && boardMeetingTo == null)
                        ? null
                        : new HashSet<>(boardApprovalListRepository
                                .findApplicationIdsInMeetingDateRange(boardMeetingFrom, boardMeetingTo));

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
                    // educational district filter — the member's WORKING district, distinct
                    // from the Location filter above (the District Office they registered at).
                    if (ed != null &&
                            (m.getEducationalDistrict() == null || !ed.equalsIgnoreCase(m.getEducationalDistrict())))
                        return false;
                    // Membership Start Date period
                    if (membershipStartFrom != null &&
                            (m.getMembershipStartDate() == null || m.getMembershipStartDate().isBefore(membershipStartFrom)))
                        return false;
                    if (membershipStartTo != null &&
                            (m.getMembershipStartDate() == null || m.getMembershipStartDate().isAfter(membershipStartTo)))
                        return false;
                    // Board Meeting Date period: approved by a meeting inside it.
                    if (approvedApplicationIds != null) {
                        Long applicationId = m.getApplication() == null ? null : m.getApplication().getId();
                        if (applicationId == null || !approvedApplicationIds.contains(applicationId))
                            return false;
                    }
                    // "Members without <document>" - unprinted only.
                    if (withoutDocument != null
                            && MembershipDocumentService.printedAt(m, withoutDocument) != null)
                        return false;
                    return true;
                })
                .sorted(memberComparator(sortBy, sortDirection))
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * The sort options MR13/MR15/MR16/MR17 list, applied here rather than in the
     * browser so a paged result is ordered across the whole result and not just the
     * page in hand.
     *
     * "District" is the submission location - the District Office the member
     * registered through - matching the District column these screens render.
     */
    private Comparator<Member> memberComparator(String sortBy, String sortDirection) {
        Comparator<String> text = Comparator.nullsLast(Comparator.naturalOrder());

        Comparator<Member> comparator = switch (sortBy == null ? "" : sortBy) {
            case "memberID" -> Comparator.comparing(Member::getMemberId, text);
            case "status" -> Comparator.comparing(
                    m -> m.getStatus() == null ? null : m.getStatus().name(), text);
            case "working-location-type" -> Comparator.comparing(Member::getWorkingLocationType, text);
            case "district" -> Comparator.comparing(Member::getSubmissionLocation, text);
            case "zone" -> Comparator.comparing(Member::getEducationalZone, text);
            default -> Comparator.comparing(
                    Member::getMembershipStartDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };

        return "desc".equalsIgnoreCase(sortDirection) ? comparator.reversed() : comparator;
    }

    public MemberDTO updateMember(Long id, MemberDTO dto) {
        Member existing = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        applyNonNullFields(existing, dto);
        Member updated = memberRepository.save(existing);

        // MMD10. Recorded after the save so a failed update does not count as
        // activity, and routed through MemberActivityService so the dormant flag
        // is cleared by the same rule everywhere.
        memberActivityService.recordActivity(updated, "MEMBER_PROFILE_UPDATE");
        auditService.record(AuditService.MODULE_MEMBER, updated.getId(), "Profile Updated");

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
        // Status is deliberately NOT copied from the DTO.
        //
        // A member's status is owned by the workflow that moves it - termination by
        // TerminationService, retirement by RetirementService, death by
        // MemberDeathRecordService - each of which enforces its own preconditions.
        // This is an ordinary profile edit open to District Office, so honouring a
        // status here let any District user PUT {"status":"TERMINATED"} and close a
        // membership outright, skipping the request, the board and Finance. It also
        // bypassed the Super-Admin-only ACTIVE guard in updateStatus() below.
        //
        // Callers that genuinely need a status change must go through updateStatus()
        // or the owning workflow.
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

        // Statuses that belong to the termination workflow and may only be reached
        // through it (SRS MMT01-MMT11).
        //
        // TERMINATED in particular asserts that the Board approved the termination AND
        // that the Finance Module has closed every savings account, so the only
        // legitimate writer is TerminationService.completeTermination(), reached from
        // the Finance callback. Allowing it here let Head Office jump a member straight
        // to TERMINATED from the directory screen, skipping the approval list and
        // leaving Finance nothing to complete.
        //
        // Note the retirement and death statuses are NOT covered yet: RETIRED,
        // RETIREMENT_APPROVED and DECEASED have exactly the same problem and want the
        // same treatment, but changing them would move those modules' behaviour, which
        // is not this change's call to make.
        if (TERMINATION_WORKFLOW_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Member status " + status + " is set by the termination workflow, not directly. "
                            + "Raise a termination request and let the Board and the Finance Module "
                            + "move the member."
            );
        }

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
        MemberStatus before = member.getStatus();

        member.setStatus(status);
        Member saved = memberRepository.save(member);
        auditService.record(AuditService.MODULE_MEMBER, saved.getId(), "Status Changed",
                before == null ? null : before.name(),
                status == null ? null : status.name(), null);
        memberStatusHistoryService.record(saved, before, status, null, "MEMBER_STATUS_UPDATED");

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

    /**
     * How many rows exist, optionally narrowed to submission locations.
     *
     * Same location contract as the search endpoint beside it: the caller states the
     * locations, an empty list means no narrowing. Answered with a COUNT rather than by
     * returning rows for the caller to measure.
     */
    public long countMembers(java.util.List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return memberRepository.count();
        }
        return memberRepository.countBySubmissionLocationIn(locations);
    }
}