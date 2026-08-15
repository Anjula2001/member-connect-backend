package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.dto.DormantApprovalListDTO;
import com.memberconnect.backend.dto.DormantConfigDTO;
import com.memberconnect.backend.dto.DormantMemberDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.DormantApprovalList;
import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.DormantApprovalListRepository;
import com.memberconnect.backend.repository.DormantConfigRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.MemberRepository;

@Service
@Transactional
public class DormantMembershipService {

    private static final List<MemberStatus> DORMANT_STATUSES = List.of(
            MemberStatus.SELECTED_FOR_DORMANT,
            MemberStatus.SENT_FOR_DORMANT_APPROVAL,
            MemberStatus.INACTIVE_DORMANT
    );

    private final MemberRepository memberRepository;
    private final DormantConfigRepository configRepository;
    private final DormantApprovalListRepository approvalListRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final LoanObligationRepository obligationRepository;

    public DormantMembershipService(
            MemberRepository memberRepository,
            DormantConfigRepository configRepository,
            DormantApprovalListRepository approvalListRepository,
            BoardmeetingRepository boardMeetingRepository,
            LoanObligationRepository obligationRepository
    ) {
        this.memberRepository = memberRepository;
        this.configRepository = configRepository;
        this.approvalListRepository = approvalListRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.obligationRepository = obligationRepository;
    }

    // ---------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------

    public DormantConfig getConfig() {
        return configRepository.findAll().stream().findFirst()
                .orElseGet(() -> configRepository.save(new DormantConfig()));
    }

    public void seedDefaultConfigIfEmpty() {
        if (configRepository.count() == 0) {
            configRepository.save(new DormantConfig());
            System.out.println("Seeded default dormant configuration.");
        }
    }

    public DormantConfigDTO getConfigDto() {
        return toConfigDto(getConfig());
    }

    public DormantConfigDTO updateConfig(DormantConfigDTO dto) {
        DormantConfig config = getConfig();
        if (dto.getDormantPeriodMonths() != null) {
            if (dto.getDormantPeriodMonths() < 1) {
                throw new RuntimeException("Dormant period must be at least 1 month");
            }
            config.setDormantPeriodMonths(dto.getDormantPeriodMonths());
        }
        if (dto.getScheduleDayOfMonth() != null) {
            if (dto.getScheduleDayOfMonth() < 1 || dto.getScheduleDayOfMonth() > 28) {
                throw new RuntimeException("Schedule day of month must be between 1 and 28");
            }
            config.setScheduleDayOfMonth(dto.getScheduleDayOfMonth());
        }
        if (dto.getScheduleHour() != null) {
            if (dto.getScheduleHour() < 0 || dto.getScheduleHour() > 23) {
                throw new RuntimeException("Schedule hour must be between 0 and 23");
            }
            config.setScheduleHour(dto.getScheduleHour());
        }
        if (dto.getScheduleMinute() != null) {
            if (dto.getScheduleMinute() < 0 || dto.getScheduleMinute() > 59) {
                throw new RuntimeException("Schedule minute must be between 0 and 59");
            }
            config.setScheduleMinute(dto.getScheduleMinute());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        return toConfigDto(configRepository.save(config));
    }

    // ---------------------------------------------------------------------
    // Identification process (MMD10 / MMD11)
    // ---------------------------------------------------------------------

    /**
     * Runs the dormant identification process. Returns [newlySelected, cleared].
     */
    public int[] runIdentification() {
        DormantConfig config = getConfig();
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusMonths(config.getDormantPeriodMonths());

        // Re-validate members already flagged: if their account has since been
        // updated, remove the dormant flag and set them back to Active.
        int cleared = 0;
        List<Member> flagged = memberRepository.findByStatusIn(List.of(
                MemberStatus.SELECTED_FOR_DORMANT,
                MemberStatus.SENT_FOR_DORMANT_APPROVAL));
        for (Member member : flagged) {
            LocalDate lastActivity = member.getLastActivityDate();
            if (lastActivity != null && !lastActivity.isBefore(cutoff)) {
                member.setStatus(MemberStatus.ACTIVE);
                member.setDormantSelectionDate(null);
                memberRepository.save(member);
                cleared++;
            }
        }

        // Flag Active members whose accounts have not been updated for the period.
        int selected = 0;
        List<Member> actives = memberRepository.findByStatus(MemberStatus.ACTIVE);
        for (Member member : actives) {
            LocalDate lastActivity = member.getLastActivityDate();
            if (lastActivity != null && lastActivity.isBefore(cutoff)) {
                member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
                member.setDormantSelectionDate(today);
                memberRepository.save(member);
                selected++;
            }
        }

        System.out.println("Dormant identification complete. Selected=" + selected + ", cleared=" + cleared);
        return new int[]{selected, cleared};
    }

    // ---------------------------------------------------------------------
    // Viewing / searching (MMD12)
    // ---------------------------------------------------------------------

    public List<String> getLocations() {
        return memberRepository.findAll().stream()
                .map(Member::getEducationalDistrict)
                .filter(loc -> loc != null && !loc.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getMemberTypes() {
        return memberRepository.findAll().stream()
                .map(Member::getMemberType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<DormantMemberDTO> searchDormantMembers(
            List<String> locations,
            String memberType,
            String dateFilter,
            LocalDate fromDate,
            LocalDate toDate,
            List<String> statuses,
            String search,
            String sortBy,
            String sortOrder
    ) {
        List<MemberStatus> statusFilter = resolveStatuses(statuses);
        List<Member> members = memberRepository.findByStatusIn(statusFilter);

        boolean allLocations = locations == null || locations.isEmpty()
                || locations.stream().anyMatch(l -> "All".equalsIgnoreCase(l));
        boolean allTypes = memberType == null || memberType.isBlank() || "All".equalsIgnoreCase(memberType);
        String searchTerm = search == null ? "" : search.trim().toLowerCase();

        LocalDate[] range = resolveDateRange(dateFilter, fromDate, toDate);
        LocalDate rangeStart = range[0];
        LocalDate rangeEnd = range[1];

        List<DormantMemberDTO> result = members.stream()
                .filter(m -> allLocations || (m.getEducationalDistrict() != null
                        && locations.stream().anyMatch(l -> l.equalsIgnoreCase(m.getEducationalDistrict()))))
                .filter(m -> allTypes || (m.getMemberType() != null
                        && m.getMemberType().equalsIgnoreCase(memberType)))
                .filter(m -> matchesDateRange(m.getDormantSelectionDate(), rangeStart, rangeEnd))
                .filter(m -> matchesSearch(m, searchTerm))
                .map(this::mapMember)
                .collect(Collectors.toList());

        sort(result, sortBy, sortOrder);
        return result;
    }

    // ---------------------------------------------------------------------
    // Approval list + board integration
    // ---------------------------------------------------------------------

    public DormantApprovalListDTO createApprovalList(DormantApprovalListDTO dto) {
        if (dto.getMemberIds() == null || dto.getMemberIds().isEmpty()) {
            throw new RuntimeException("No dormant members selected for the approval list");
        }
        if (dto.getBoardMeetingId() == null) {
            throw new RuntimeException("Board meeting is required");
        }

        BoardMeeting boardMeeting = boardMeetingRepository.findById(dto.getBoardMeetingId())
                .orElseThrow(() -> new RuntimeException("Board Meeting not found"));

        DormantApprovalList entity = new DormantApprovalList();
        entity.setListId("DAL-" + System.currentTimeMillis());
        entity.setBoardMeetingId(boardMeeting.getId());
        entity.setBoardMeetingDate(boardMeeting.getScheduledDate());
        entity.setStatus("CREATED");
        entity.setCreatedAt(LocalDateTime.now());

        for (String memberId : dto.getMemberIds()) {
            Member member = memberRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));

            if (member.getStatus() != MemberStatus.SELECTED_FOR_DORMANT) {
                throw new RuntimeException(
                        "Only 'Selected for Dormant' members can be added to an approval list: " + memberId);
            }
            if (obligationRepository.existsByMemberId(memberId)) {
                throw new RuntimeException(
                        "Member has indirect loan obligations and cannot be inactivated: " + memberId);
            }

            member.setStatus(MemberStatus.SENT_FOR_DORMANT_APPROVAL);
            memberRepository.save(member);
            entity.getMembers().add(member);
        }

        return toDto(approvalListRepository.save(entity));
    }

    public List<DormantApprovalListDTO> getAllApprovalLists() {
        return approvalListRepository.findAllWithMembers().stream()
                .map(this::toDto)
                .toList();
    }

    public DormantApprovalListDTO getApprovalList(String listId) {
        DormantApprovalList entity = approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> new RuntimeException("Dormant approval list not found"));
        return toDto(entity);
    }

    public List<DormantMemberDTO> getMembersByListId(String listId) {
        DormantApprovalList entity = approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> new RuntimeException("Dormant approval list not found"));
        return entity.getMembers().stream().map(this::mapMember).collect(Collectors.toList());
    }

    public DormantApprovalListDTO processApprovalList(String listId, DormantApprovalListDTO dto) {
        DormantApprovalList entity = approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> new RuntimeException("Dormant approval list not found"));

        if (dto.getDecision() == null || dto.getDecision().isBlank()) {
            throw new RuntimeException("Decision is required");
        }
        String decision = dto.getDecision().trim();
        boolean approved = "APPROVE".equalsIgnoreCase(decision);
        boolean rejected = "REJECT".equalsIgnoreCase(decision);
        if (!approved && !rejected) {
            throw new RuntimeException("Decision must be Approve or Reject");
        }
        if (rejected && (dto.getRejectReason() == null || dto.getRejectReason().isBlank())) {
            throw new RuntimeException("Reject reason is required when rejecting the list");
        }

        entity.setStatus("PROCESSED");
        entity.setProcessedAt(LocalDateTime.now());
        entity.setProcessedBy(defaultString(dto.getProcessedBy(), "Head Office User"));
        entity.setActualMeetingDate(dto.getActualMeetingDate() != null ? dto.getActualMeetingDate() : LocalDate.now());
        entity.setDecision(approved ? "Approve" : "Reject");
        entity.setRejectReason(rejected ? dto.getRejectReason().trim() : null);
        entity.setBoardRemarks(dto.getBoardRemarks());

        // When the board rejects, the members go back to 'Selected for Dormant'.
        if (rejected) {
            for (Member member : entity.getMembers()) {
                if (member.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL) {
                    member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
                    memberRepository.save(member);
                }
            }
        }

        return toDto(approvalListRepository.save(entity));
    }

    /**
     * Approve or reject a single Dormant Member within an approval list.
     * Approve inactivates the member (INACTIVE_DORMANT); reject rolls the member
     * back to SELECTED_FOR_DORMANT and detaches them from the list.
     */
    public DormantApprovalListDTO decideMember(String listId, String memberId, String decision) {
        DormantApprovalList entity = approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> new RuntimeException("Dormant approval list not found"));

        Member member = entity.getMembers().stream()
                .filter(m -> memberId.equals(m.getMemberId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Member is not part of this list: " + memberId));

        if (member.getStatus() != MemberStatus.SENT_FOR_DORMANT_APPROVAL) {
            throw new RuntimeException("Member is not pending approval: " + memberId);
        }

        boolean approved = "APPROVE".equalsIgnoreCase(decision);
        boolean rejected = "REJECT".equalsIgnoreCase(decision);
        if (!approved && !rejected) {
            throw new RuntimeException("Decision must be Approve or Reject");
        }

        if (approved) {
            member.setStatus(MemberStatus.INACTIVE_DORMANT);
            memberRepository.save(member);
        } else {
            member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
            memberRepository.save(member);
            entity.getMembers().removeIf(m -> memberId.equals(m.getMemberId()));
        }

        entity.setActualMeetingDate(entity.getActualMeetingDate() != null
                ? entity.getActualMeetingDate() : LocalDate.now());
        entity.setProcessedBy(defaultString(entity.getProcessedBy(), "Head Office User"));

        boolean anyPending = entity.getMembers().stream()
                .anyMatch(m -> m.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        boolean anyInactivated = entity.getMembers().stream()
                .anyMatch(m -> m.getStatus() == MemberStatus.INACTIVE_DORMANT);

        if (anyPending) {
            entity.setStatus("IN_PROGRESS");
        } else {
            entity.setStatus(anyInactivated ? "INACTIVATED" : "PROCESSED");
            entity.setProcessedAt(LocalDateTime.now());
            if (anyInactivated) {
                entity.setInactivatedAt(LocalDateTime.now());
            }
        }

        return toDto(approvalListRepository.save(entity));
    }

    /**
     * Bulk inactivation after board approval. If memberIds is null/empty every
     * member on the list is inactivated.
     */
    public DormantApprovalListDTO inactivateMembers(String listId, List<String> memberIds) {
        DormantApprovalList entity = approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> new RuntimeException("Dormant approval list not found"));

        if (!"PROCESSED".equalsIgnoreCase(entity.getStatus()) || !"Approve".equalsIgnoreCase(entity.getDecision())) {
            throw new RuntimeException("Members can only be inactivated after board approval");
        }

        boolean all = memberIds == null || memberIds.isEmpty();
        for (Member member : entity.getMembers()) {
            boolean selected = all || memberIds.contains(member.getMemberId());
            if (selected && member.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL) {
                member.setStatus(MemberStatus.INACTIVE_DORMANT);
                memberRepository.save(member);
            }
        }

        boolean anyPending = entity.getMembers().stream()
                .anyMatch(m -> m.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        entity.setStatus(anyPending ? "PROCESSED" : "INACTIVATED");
        entity.setInactivatedAt(LocalDateTime.now());
        return toDto(approvalListRepository.save(entity));
    }

    public String deleteApprovalList(String listId) {
        DormantApprovalList entity = approvalListRepository.findByListIdWithMembers(listId)
                .orElseThrow(() -> new RuntimeException("Dormant approval list not found"));

        for (Member member : entity.getMembers()) {
            if (member.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL) {
                member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
                memberRepository.save(member);
            }
        }
        approvalListRepository.delete(entity);
        return "Dormant approval list deleted successfully";
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<MemberStatus> resolveStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of(MemberStatus.SELECTED_FOR_DORMANT, MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        }
        List<MemberStatus> resolved = new ArrayList<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            try {
                MemberStatus parsed = MemberStatus.valueOf(status.trim().toUpperCase());
                if (DORMANT_STATUSES.contains(parsed)) {
                    resolved.add(parsed);
                }
            } catch (IllegalArgumentException ignored) {
                // skip unknown status values
            }
        }
        if (resolved.isEmpty()) {
            return List.of(MemberStatus.SELECTED_FOR_DORMANT, MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        }
        return resolved;
    }

    private LocalDate[] resolveDateRange(String dateFilter, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();
        if (dateFilter == null) {
            return new LocalDate[]{null, null};
        }
        switch (dateFilter.trim().toLowerCase()) {
            case "thismonth": {
                LocalDate start = today.withDayOfMonth(1);
                LocalDate end = start.plusMonths(1).minusDays(1);
                return new LocalDate[]{start, end};
            }
            case "thisandlastmonth": {
                LocalDate start = today.withDayOfMonth(1).minusMonths(1);
                LocalDate end = today.withDayOfMonth(1).plusMonths(1).minusDays(1);
                return new LocalDate[]{start, end};
            }
            case "dateperiod":
                return new LocalDate[]{fromDate, toDate};
            case "all":
            default:
                return new LocalDate[]{null, null};
        }
    }

    private boolean matchesDateRange(LocalDate date, LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return end == null || !date.isAfter(end);
    }

    private boolean matchesSearch(Member member, String term) {
        if (term.isEmpty()) {
            return true;
        }
        return contains(member.getFullName(), term)
                || contains(member.getNameWithInitials(), term)
                || contains(member.getMemberId(), term)
                || contains(member.getNic(), term);
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private void sort(List<DormantMemberDTO> members, String sortBy, String sortOrder) {
        Comparator<DormantMemberDTO> comparator;
        String key = sortBy == null ? "lastactivity" : sortBy.trim().toLowerCase();
        switch (key) {
            case "membershipdate":
                comparator = Comparator.comparing(DormantMemberDTO::getMembershipDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "memberid":
                comparator = Comparator.comparing(DormantMemberDTO::getMemberId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "lastactivity":
            default:
                comparator = Comparator.comparing(DormantMemberDTO::getLastActivityDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
        }
        if (!"asc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        members.sort(comparator);
    }

    private DormantMemberDTO mapMember(Member member) {
        DormantMemberDTO dto = new DormantMemberDTO();
        dto.setId(member.getId());
        dto.setMemberId(member.getMemberId());
        dto.setFullName(member.getFullName());
        dto.setNameWithInitials(member.getNameWithInitials());
        dto.setNic(member.getNic());
        dto.setMemberType(member.getMemberType());
        dto.setLocation(member.getEducationalDistrict());
        dto.setLastActivityDate(member.getLastActivityDate());
        dto.setMembershipDate(member.getMembershipStartDate());
        dto.setDormantSelectionDate(member.getDormantSelectionDate());
        dto.setStatus(member.getStatus() != null ? member.getStatus().name() : null);
        dto.setHasIndirectObligations(
                member.getMemberId() != null && obligationRepository.existsByMemberId(member.getMemberId()));
        return dto;
    }

    private DormantApprovalListDTO toDto(DormantApprovalList entity) {
        DormantApprovalListDTO dto = new DormantApprovalListDTO();
        dto.setId(entity.getId());
        dto.setListId(entity.getListId());
        dto.setBoardMeetingId(entity.getBoardMeetingId());
        dto.setBoardMeetingDate(entity.getBoardMeetingDate());
        dto.setActualMeetingDate(entity.getActualMeetingDate());
        dto.setMemberIds(entity.getMembers().stream().map(Member::getMemberId).collect(Collectors.toList()));
        dto.setMembers(entity.getMembers().stream().map(this::mapMember).collect(Collectors.toList()));
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setProcessedBy(entity.getProcessedBy());
        dto.setDecision(entity.getDecision());
        dto.setRejectReason(entity.getRejectReason());
        dto.setBoardRemarks(entity.getBoardRemarks());
        dto.setInactivatedAt(entity.getInactivatedAt());
        return dto;
    }

    private DormantConfigDTO toConfigDto(DormantConfig config) {
        DormantConfigDTO dto = new DormantConfigDTO();
        dto.setDormantPeriodMonths(config.getDormantPeriodMonths());
        dto.setScheduleDayOfMonth(config.getScheduleDayOfMonth());
        dto.setScheduleHour(config.getScheduleHour());
        dto.setScheduleMinute(config.getScheduleMinute());
        dto.setEnabled(config.getEnabled());
        return dto;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
