package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.MemberDTO;
import com.memberconnect.backend.dto.MemberSearchPageDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.memberconnect.backend.enums.MembershipDocumentType;
import com.memberconnect.backend.repository.BoardApprovalListRepository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
@Service
@Transactional
@SuppressWarnings("null")
public class MemberService {

    /** Matches DEFAULT_PAGE_SIZE in the frontend's TablePagination control. */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** Guards against a caller asking for the whole membership in one page. */
    private static final int MAX_PAGE_SIZE = 200;


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

        List<Member> matched = memberRepository.findAll(
                memberFilter(query, statuses, locations, workingLocationType, educationalZone,
                        educationalDistrict, membershipStartFrom, membershipStartTo,
                        withoutDocument, boardMeetingFrom, boardMeetingTo),
                MemberSpecifications.sort(sortBy, sortDirection));

        return matched.stream().map(this::convertToDTO).toList();
    }

    /**
     * One page of the Member Directory, filtered, sorted and sliced by the database.
     *
     * The unpaged overload above still exists for the callers that genuinely need
     * every row — the printable Directory report, the account-linking picker, the
     * dashboard counter — and it now runs the same SQL predicate rather than reading
     * the table. This method exists for the screens that show ten rows at a time, so
     * that the other pages are never fetched at all and the footer's total comes from
     * a COUNT rather than from the length of a list nobody asked for.
     */
    public MemberSearchPageDTO searchMembersPage(String query, List<MemberStatus> statuses, List<String> locations,
                                                 String workingLocationType, String educationalZone,
                                                 String educationalDistrict,
                                                 LocalDate membershipStartFrom, LocalDate membershipStartTo,
                                                 MembershipDocumentType withoutDocument,
                                                 LocalDate boardMeetingFrom, LocalDate boardMeetingTo,
                                                 String sortBy, String sortDirection,
                                                 Integer page, Integer size) {

        Specification<Member> spec = memberFilter(query, statuses, locations, workingLocationType,
                educationalZone, educationalDistrict, membershipStartFrom, membershipStartTo,
                withoutDocument, boardMeetingFrom, boardMeetingTo);

        Sort sort = MemberSpecifications.sort(sortBy, sortDirection);
        int pageNumber = (page == null || page < 0) ? 0 : page;
        int pageSize = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        Page<Member> result = memberRepository.findAll(spec, PageRequest.of(pageNumber, pageSize, sort));

        // Narrowing the filter, or a member leaving the result, can leave the requested
        // page past the end of what remains. Answering with the last page that does
        // exist saves the browser from noticing an empty page and asking again; the
        // page actually returned is reported back so the caller can follow.
        if (pageNumber > 0 && result.getContent().isEmpty()) {
            pageNumber = result.getTotalPages() == 0 ? 0 : result.getTotalPages() - 1;
            result = memberRepository.findAll(spec, PageRequest.of(pageNumber, pageSize, sort));
        }

        return new MemberSearchPageDTO(
                result.getContent().stream().map(this::convertToDTO).toList(),
                pageNumber,
                pageSize,
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * The shared predicate behind both overloads.
     *
     * The board meeting period is resolved to a set of application ids once here
     * rather than per member. Null means "any meeting" — the spec default — and is
     * deliberately distinct from an empty set, which means a period was given and no
     * meeting inside it approved anybody.
     */
    private Specification<Member> memberFilter(String query, List<MemberStatus> statuses, List<String> locations,
                                               String workingLocationType, String educationalZone,
                                               String educationalDistrict,
                                               LocalDate membershipStartFrom, LocalDate membershipStartTo,
                                               MembershipDocumentType withoutDocument,
                                               LocalDate boardMeetingFrom, LocalDate boardMeetingTo) {

        final Set<Long> approvedApplicationIds =
                (boardMeetingFrom == null && boardMeetingTo == null)
                        ? null
                        : new HashSet<>(boardApprovalListRepository
                                .findApplicationIdsInMeetingDateRange(boardMeetingFrom, boardMeetingTo));

        return MemberSpecifications.filter(query, statuses, locations, workingLocationType,
                educationalZone, educationalDistrict, membershipStartFrom, membershipStartTo,
                approvedApplicationIds, withoutDocument);
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