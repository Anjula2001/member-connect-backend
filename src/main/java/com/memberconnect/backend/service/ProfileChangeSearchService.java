package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.ProfileChangeListItemDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.MemberTransferStatus;
import com.memberconnect.backend.enums.ProfileChangeSortBy;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.enums.RequestReceivedOn;
import com.memberconnect.backend.model.BasicProfileChangeRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.model.NameChangeRequest;
import com.memberconnect.backend.model.NommineChangeRequests;
import com.memberconnect.backend.model.ProfileChangeRequest;
import com.memberconnect.backend.model.RemittanceAmountChange;
import com.memberconnect.backend.repository.BasicProfileChangeRequestRepo;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MemberTransferRepository;
import com.memberconnect.backend.repository.NameChangeRequestRepo;
import com.memberconnect.backend.repository.NominneChangeRequestRepo;
import com.memberconnect.backend.repository.RemittanceAmountChangeRepo;

/**
 * Serves the "All Member Profile Change Requests List" (Requirement 02,
 * MMC02 / MMC06 / MMC15 / MMC19) — one screen listing every profile change request
 * type, with the SRS's Location, Type, Request Received On, Status, Search and Sort
 * filters.
 *
 * Previously the screen called each type's "get all" endpoint in turn and filtered in
 * the browser: Location, Received On and Sort did not exist at all, Status was a
 * single-select defaulting to ALL rather than a multi-select defaulting to Submitted
 * for Approval, and the search box was bound to state that was never read.
 *
 * Note that this is not an access boundary. Every caller may search every location -
 * see ProfileChangeController for why the district lock was removed. The value of
 * filtering here is correctness and volume, not authorisation.
 *
 * The five types live in five tables with no common parent table and no association to
 * Member, so this merges in memory rather than in SQL. Two things keep that honest:
 * the per-type filtering is pushed down as a Specification, and the members are
 * resolved with one findByMemberIdIn call for the whole result set rather than one
 * lookup per row.
 */
@Service
public class ProfileChangeSearchService {

    private final BasicProfileChangeRequestRepo basicProfileRepo;
    private final NameChangeRequestRepo nameChangeRepo;
    private final NominneChangeRequestRepo nomineeChangeRepo;
    private final RemittanceAmountChangeRepo remittanceChangeRepo;
    private final MemberTransferRepository memberTransferRepo;
    private final MemberRepository memberRepository;

    public ProfileChangeSearchService(
            BasicProfileChangeRequestRepo basicProfileRepo,
            NameChangeRequestRepo nameChangeRepo,
            NominneChangeRequestRepo nomineeChangeRepo,
            RemittanceAmountChangeRepo remittanceChangeRepo,
            MemberTransferRepository memberTransferRepo,
            MemberRepository memberRepository
    ) {
        this.basicProfileRepo = basicProfileRepo;
        this.nameChangeRepo = nameChangeRepo;
        this.nomineeChangeRepo = nomineeChangeRepo;
        this.remittanceChangeRepo = remittanceChangeRepo;
        this.memberTransferRepo = memberTransferRepo;
        this.memberRepository = memberRepository;
    }

    public List<ProfileChangeListItemDTO> search(
            Collection<ProfileChangeType> types,
            Collection<ApplicationStatus> statuses,
            Collection<String> locations,
            RequestReceivedOn receivedOn,
            LocalDate from,
            LocalDate to,
            String search,
            ProfileChangeSortBy sortBy,
            boolean descending
    ) {
        Set<ProfileChangeType> wanted = (types == null || types.isEmpty())
                ? EnumSet.allOf(ProfileChangeType.class)
                : EnumSet.copyOf(types);

        LocalDate[] range = resolveDateRange(receivedOn, from, to);
        LocalDate rangeFrom = range[0];
        LocalDate rangeTo = range[1];

        List<ProfileChangeListItemDTO> rows = new ArrayList<>();

        if (wanted.contains(ProfileChangeType.BASIC_PROFILE)) {
            rows.addAll(collect(
                    basicProfileRepo, ProfileChangeType.BASIC_PROFILE,
                    BasicProfileChangeRequest::getId,
                    statuses, locations, rangeFrom, rangeTo
            ));
        }
        if (wanted.contains(ProfileChangeType.NAME)) {
            rows.addAll(collect(
                    nameChangeRepo, ProfileChangeType.NAME,
                    NameChangeRequest::getNameChangeRequestID,
                    statuses, locations, rangeFrom, rangeTo
            ));
        }
        if (wanted.contains(ProfileChangeType.NOMINEE)) {
            rows.addAll(collect(
                    nomineeChangeRepo, ProfileChangeType.NOMINEE,
                    NommineChangeRequests::getId,
                    statuses, locations, rangeFrom, rangeTo
            ));
        }
        if (wanted.contains(ProfileChangeType.REMITTANCE)) {
            rows.addAll(collect(
                    remittanceChangeRepo, ProfileChangeType.REMITTANCE,
                    RemittanceAmountChange::getId,
                    statuses, locations, rangeFrom, rangeTo
            ));
        }

        if (wanted.contains(ProfileChangeType.MEMBER_TRANSFER)) {
            rows.addAll(collectTransfers(statuses, locations, rangeFrom, rangeTo));
        }

        attachMemberDetails(rows);

        List<ProfileChangeListItemDTO> matched = applySearch(rows, search);
        matched.sort(comparator(sortBy, descending));
        return matched;
    }

    /**
     * Runs the shared filter against one repository and maps the results to list rows.
     * The id accessor is passed in because each entity names its primary key
     * differently (id, nameChangeRequestID).
     */
    private <T extends ProfileChangeRequest> List<ProfileChangeListItemDTO> collect(
            JpaSpecificationExecutor<T> repository,
            ProfileChangeType type,
            Function<T, Integer> idAccessor,
            Collection<ApplicationStatus> statuses,
            Collection<String> locations,
            LocalDate from,
            LocalDate to
    ) {
        Specification<T> spec = ProfileChangeSpecifications.filter(statuses, locations, from, to);

        return repository.findAll(spec).stream()
                .map(entity -> {
                    ProfileChangeListItemDTO row = new ProfileChangeListItemDTO();
                    row.setType(type);
                    row.setTypeLabel(type.getLabel());
                    row.setRequestId(idAccessor.apply(entity));
                    row.setRequestNo(entity.getRequestNo());
                    row.setStatus(entity.getStatus());
                    row.setRequestedDate(entity.getRequestedDate());
                    row.setSubmissionLocation(entity.getSubmissionLocation());
                    row.setMemberId(entity.getMemberId());
                    return row;
                })
                .collect(Collectors.toList());
    }

    /**
     * Member Transfers, collected separately from the other four.
     *
     * MemberTransferRequest does not extend ProfileChangeRequest, so the shared collect()
     * cannot reach it, and it stores MemberTransferStatus rather than ApplicationStatus.
     * Both statuses are mapped onto the shared vocabulary here so one Status filter, one
     * sort and one results table serve all five types - which is what MMC28 asks for.
     */
    private List<ProfileChangeListItemDTO> collectTransfers(
            Collection<ApplicationStatus> statuses,
            Collection<String> locations,
            LocalDate from,
            LocalDate to
    ) {
        Set<MemberTransferStatus> transferStatuses = (statuses == null || statuses.isEmpty())
                ? EnumSet.noneOf(MemberTransferStatus.class)
                : statuses.stream()
                        .map(ProfileChangeSearchService::toTransferStatus)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(MemberTransferStatus.class)));

        // A status filter that names only statuses transfers cannot hold - "Added to
        // Board Approval List", say - must return no transfers rather than all of them.
        if (statuses != null && !statuses.isEmpty() && transferStatuses.isEmpty()) {
            return List.of();
        }

        Specification<MemberTransferRequest> spec =
                ProfileChangeSpecifications.transferFilter(transferStatuses, locations, from, to);

        return memberTransferRepo.findAll(spec).stream()
                .map(entity -> {
                    ProfileChangeListItemDTO row = new ProfileChangeListItemDTO();
                    row.setType(ProfileChangeType.MEMBER_TRANSFER);
                    row.setTypeLabel(ProfileChangeType.MEMBER_TRANSFER.getLabel());
                    // The transfer table's key is a Long; the shared row carries an
                    // Integer because the other four use one.
                    row.setRequestId(entity.getId() == null ? null : entity.getId().intValue());
                    row.setRequestNo(entity.getRequestId());
                    row.setStatus(toApplicationStatus(entity.getStatus()));
                    row.setRequestedDate(entity.getRequestedDate());
                    Member member = entity.getMember();
                    if (member != null) {
                        row.setSubmissionLocation(member.getSubmissionLocation());
                        row.setMemberId(member.getMemberId());
                    }
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** MemberTransferStatus spells its submitted state without underscores. */
    private static ApplicationStatus toApplicationStatus(MemberTransferStatus status) {
        if (status == null) return null;
        return switch (status) {
            case SUBMITTEDFORAPPROVAL -> ApplicationStatus.SUBMITTED_FOR_APPROVAL;
            case APPROVED -> ApplicationStatus.APPROVED;
            case REJECTED -> ApplicationStatus.REJECTED;
            case INACTIVE -> ApplicationStatus.INACTIVE;
        };
    }

    /** The reverse, for filtering. Null means "transfers have no such status". */
    private static MemberTransferStatus toTransferStatus(ApplicationStatus status) {
        if (status == null) return null;
        return switch (status) {
            case SUBMITTED_FOR_APPROVAL -> MemberTransferStatus.SUBMITTEDFORAPPROVAL;
            case APPROVED -> MemberTransferStatus.APPROVED;
            case REJECTED -> MemberTransferStatus.REJECTED;
            case INACTIVE -> MemberTransferStatus.INACTIVE;
            default -> null;
        };
    }

    /**
     * Resolves each row's member in one round trip. Rows whose member cannot be found
     * — including the Name and Nominee requests raised before those tables had a
     * memberId column at all — keep their null name fields and simply never match a
     * name or NIC search.
     */
    private void attachMemberDetails(List<ProfileChangeListItemDTO> rows) {
        Set<String> memberIds = rows.stream()
                .map(ProfileChangeListItemDTO::getMemberId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (memberIds.isEmpty()) {
            return;
        }

        Map<String, Member> byMemberId = new HashMap<>();
        for (Member member : memberRepository.findByMemberIdIn(memberIds)) {
            byMemberId.put(member.getMemberId(), member);
        }

        for (ProfileChangeListItemDTO row : rows) {
            Member member = row.getMemberId() == null ? null : byMemberId.get(row.getMemberId());
            if (member == null) {
                continue;
            }
            row.setFullName(member.getFullName());
            row.setNameAsInPayroll(member.getNameAsInPayroll());
            row.setNameWithInitials(member.getNameWithInitials());
            row.setNic(member.getNic());
        }
    }

    /**
     * MMC02: "The search key entered by the user will be used to search through the
     * Members' Full Name, Name as in Payroll, Name with Initials, Member Number and the
     * NIC Number fields. Records with any similar matches will be retrieved."
     */
    private List<ProfileChangeListItemDTO> applySearch(List<ProfileChangeListItemDTO> rows, String search) {
        if (search == null || search.isBlank()) {
            return rows;
        }

        String needle = search.trim().toLowerCase();

        return rows.stream()
                .filter(row -> contains(row.getFullName(), needle)
                        || contains(row.getNameAsInPayroll(), needle)
                        || contains(row.getNameWithInitials(), needle)
                        || contains(row.getMemberId(), needle)
                        || contains(row.getNic(), needle))
                .collect(Collectors.toList());
    }

    private boolean contains(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase().contains(lowercaseNeedle);
    }

    /**
     * Turns the SRS's period options into a concrete date range. ALL_DAYS and a
     * DATE_PERIOD with no dates both mean "no bound", which is why nulls are returned
     * rather than defaulted.
     */
    private LocalDate[] resolveDateRange(RequestReceivedOn receivedOn, LocalDate from, LocalDate to) {
        RequestReceivedOn period = receivedOn == null ? RequestReceivedOn.ALL_DAYS : receivedOn;
        LocalDate today = LocalDate.now();

        return switch (period) {
            case THIS_MONTH -> new LocalDate[] { today.withDayOfMonth(1), today };
            case THIS_AND_LAST_MONTH -> new LocalDate[] { today.minusMonths(1).withDayOfMonth(1), today };
            case DATE_PERIOD -> new LocalDate[] { from, to };
            case ALL_DAYS -> new LocalDate[] { null, null };
        };
    }

    /**
     * Nulls sort last in every direction: an undated or unnumbered legacy row should
     * not occupy the top of the list ahead of live requests.
     */
    private Comparator<ProfileChangeListItemDTO> comparator(ProfileChangeSortBy sortBy, boolean descending) {
        ProfileChangeSortBy key = sortBy == null ? ProfileChangeSortBy.REQUESTED_DATE : sortBy;

        Comparator<ProfileChangeListItemDTO> comparator = switch (key) {
            case STATUS -> Comparator.comparing(
                    row -> row.getStatus() == null ? null : row.getStatus().name(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case MEMBER_ID -> Comparator.comparing(
                    ProfileChangeListItemDTO::getMemberId,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case REQUESTED_DATE -> Comparator.comparing(
                    ProfileChangeListItemDTO::getRequestedDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };

        return descending ? comparator.reversed() : comparator;
    }
}
