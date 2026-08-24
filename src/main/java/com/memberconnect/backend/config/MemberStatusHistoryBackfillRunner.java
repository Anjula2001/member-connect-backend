package com.memberconnect.backend.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.enums.MemberDeathRecordStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.RetirementRequestStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.model.MemberStatusHistory;
import com.memberconnect.backend.model.RetirementRequest;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MemberStatusHistoryRepository;
import com.memberconnect.backend.repository.RetirementRequestRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

/**
 * One-time backfill of member_status_history from the records that already carry a
 * business date, so scholarship eligibility can be judged on a member's status on a
 * past date rather than only on changes made from now on.
 *
 * <h2>What it writes, and what it deliberately does not</h2>
 *
 * Two rows at most per member:
 *
 * <ol>
 *   <li>an ACTIVE anchor at membershipStartDate - a member is active from the day
 *       their membership starts;</li>
 *   <li>the start of the non-ACTIVE period the member is in <em>now</em>, where a
 *       record exists that dates it: a termination's or retirement's effective date,
 *       a deceased date, a dormant selection date.</li>
 * </ol>
 *
 * It does not attempt to reconstruct non-ACTIVE periods that have since <em>ended</em>
 * - a termination that was rejected, a death record made inactive. Their start dates
 * are recorded but nothing anywhere records when the member came back, and a period
 * written with a start and no end would read as "inactive from then on", wrongly
 * rejecting every scholarship request for an exam sat after the member was already
 * back. A gap that lets a request through is recoverable; a fabricated end date that
 * refuses one is not.
 *
 * The same reasoning covers members whose current status has no dated record at all
 * (INACTIVE and RESIGNED are set directly, with no request behind them): no row is
 * written, and MemberStatusHistoryService.statusOn returns "not known", which the
 * scholarship validation treats as no evidence of inactivity.
 *
 * <h2>Safety</h2>
 *
 * Guarded on the table being completely empty, in the same style as the seeders: once
 * any row exists - written by this runner or by a live status change - the whole
 * backfill is skipped, so it can never duplicate rows or overwrite real history.
 *
 * Runs at order 10, after the seeders and the other backfills, since it only reads
 * data they may have created.
 */
@Component
@Order(10)
public class MemberStatusHistoryBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MemberStatusHistoryBackfillRunner.class);

    /** Termination request states that hold the member at TERMINATION_REQUESTED. */
    private static final Set<TerminationRequestStatus> TERMINATION_IN_PROGRESS = Set.of(
            TerminationRequestStatus.NEW,
            TerminationRequestStatus.INCOMPLETE,
            TerminationRequestStatus.SUBMITTED_FOR_APPROVAL,
            TerminationRequestStatus.ADDED_TO_APPROVAL_LIST
    );

    /** Retirement request states that hold the member at RETIREMENT_REQUESTED. */
    private static final Set<RetirementRequestStatus> RETIREMENT_IN_PROGRESS = Set.of(
            RetirementRequestStatus.NEW,
            RetirementRequestStatus.INCOMPLETE,
            RetirementRequestStatus.SUBMITTED_FOR_APPROVAL
    );

    private final MemberRepository memberRepository;
    private final MemberStatusHistoryRepository historyRepository;
    private final TerminationRequestRepository terminationRepository;
    private final RetirementRequestRepository retirementRepository;
    private final MemberDeathRecordRepository deathRecordRepository;

    public MemberStatusHistoryBackfillRunner(
            MemberRepository memberRepository,
            MemberStatusHistoryRepository historyRepository,
            TerminationRequestRepository terminationRepository,
            RetirementRequestRepository retirementRepository,
            MemberDeathRecordRepository deathRecordRepository
    ) {
        this.memberRepository = memberRepository;
        this.historyRepository = historyRepository;
        this.terminationRepository = terminationRepository;
        this.retirementRepository = retirementRepository;
        this.deathRecordRepository = deathRecordRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (historyRepository.count() > 0) {
            return;
        }

        List<Member> members = memberRepository.findAll();
        if (members.isEmpty()) {
            return;
        }

        // Each table read once and indexed, rather than a query per member
        Map<String, List<TerminationRequest>> terminationsByMember = terminationRepository.findAll().stream()
                .filter(request -> request.getMemberId() != null)
                .collect(Collectors.groupingBy(TerminationRequest::getMemberId));

        Map<String, List<RetirementRequest>> retirementsByMember = retirementRepository.findAll().stream()
                .filter(request -> request.getMemberId() != null)
                .collect(Collectors.groupingBy(RetirementRequest::getMemberId));

        Map<String, List<MemberDeathRecord>> deathRecordsByMember = deathRecordRepository.findAllWithMember().stream()
                .filter(record -> record.getMember() != null && record.getMember().getMemberId() != null)
                .collect(Collectors.groupingBy(record -> record.getMember().getMemberId()));

        List<MemberStatusHistory> rows = new ArrayList<>();
        int anchored = 0;
        int episodes = 0;
        int undated = 0;

        for (Member member : members) {
            String memberId = member.getMemberId();
            if (memberId == null) {
                continue;
            }

            MemberStatus current = member.getStatus();
            boolean isActive = current == null || current == MemberStatus.ACTIVE;

            LocalDate episodeStart = isActive ? null : currentEpisodeStart(
                    member,
                    terminationsByMember.getOrDefault(memberId, List.of()),
                    retirementsByMember.getOrDefault(memberId, List.of()),
                    deathRecordsByMember.getOrDefault(memberId, List.of())
            );

            if (!isActive && episodeStart == null) {
                undated++;
            }

            LocalDate membershipStart = member.getMembershipStartDate();

            // The anchor is only meaningful while it precedes the period that followed
            // it. A membership start on or after the date the member left ACTIVE means
            // one of the two dates is wrong, and the anchor is the one worth dropping:
            // kept, it would sort last and report the member as active ever since.
            boolean anchorUsable = membershipStart != null
                    && (episodeStart == null || membershipStart.isBefore(episodeStart));

            if (anchorUsable) {
                rows.add(row(member, null, MemberStatus.ACTIVE, membershipStart,
                        "BACKFILL_MEMBERSHIP_START",
                        "Membership started " + membershipStart));
                anchored++;
            }

            if (episodeStart != null) {
                rows.add(row(member, MemberStatus.ACTIVE, current, episodeStart,
                        "BACKFILL_CURRENT_STATUS",
                        "Derived from the record that dates the member's current status"));
                episodes++;
            }
        }

        if (!rows.isEmpty()) {
            historyRepository.saveAll(rows);
        }

        log.info(
                "Member status history backfilled: {} rows for {} members "
                        + "({} membership-start anchors, {} dated current statuses, "
                        + "{} non-active members left unrecorded for want of a dated record)",
                rows.size(), members.size(), anchored, episodes, undated
        );
    }

    /**
     * The date the member left ACTIVE for the status they hold now, or null when
     * nothing records it.
     */
    private LocalDate currentEpisodeStart(
            Member member,
            List<TerminationRequest> terminations,
            List<RetirementRequest> retirements,
            List<MemberDeathRecord> deathRecords
    ) {
        return switch (member.getStatus()) {
            case TERMINATION_REQUESTED -> latestTermination(terminations, TERMINATION_IN_PROGRESS)
                    .map(TerminationRequest::getRequestedDate)
                    .orElse(null);

            // The termination's own effective date, which is the date the membership
            // ends - not the date the board happened to sit
            case TERMINATION_APPROVED, TERMINATED -> latestTermination(
                    terminations, Set.of(TerminationRequestStatus.APPROVED))
                    .map(TerminationRequest::getEffectiveDate)
                    .orElse(null);

            case RETIREMENT_REQUESTED -> latestRetirement(retirements, RETIREMENT_IN_PROGRESS)
                    .map(RetirementRequest::getRequestedDate)
                    .orElse(null);

            case RETIREMENT_APPROVED, RETIRED -> latestRetirement(
                    retirements, Set.of(RetirementRequestStatus.APPROVED))
                    .map(RetirementRequest::getEffectiveDate)
                    .orElse(null);

            // The member stopped being active when they died, not when it was recorded
            case MEMBER_DEATH_RECORDED, MEMBER_DEATH_APPROVED, DECEASED -> deathRecords.stream()
                    .filter(record -> record.getStatus() != MemberDeathRecordStatus.INACTIVE)
                    .map(MemberDeathRecord::getDeceasedDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            case SELECTED_FOR_DORMANT, SENT_FOR_DORMANT_APPROVAL, INACTIVE_DORMANT ->
                    member.getDormantSelectionDate();

            // INACTIVE and RESIGNED are set directly, with no dated record behind them
            default -> null;
        };
    }

    private java.util.Optional<TerminationRequest> latestTermination(
            List<TerminationRequest> requests, Set<TerminationRequestStatus> statuses) {
        return requests.stream()
                .filter(request -> statuses.contains(request.getStatus()))
                .max(Comparator.comparing(
                        TerminationRequest::getRequestedDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private java.util.Optional<RetirementRequest> latestRetirement(
            List<RetirementRequest> requests, Set<RetirementRequestStatus> statuses) {
        return requests.stream()
                .filter(request -> statuses.contains(request.getStatus()))
                .max(Comparator.comparing(
                        RetirementRequest::getRequestedDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private MemberStatusHistory row(
            Member member,
            MemberStatus from,
            MemberStatus to,
            LocalDate effectiveDate,
            String source,
            String remarks
    ) {
        MemberStatusHistory history = new MemberStatusHistory();
        history.setMember(member);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setEffectiveDate(effectiveDate);
        history.setSource(source);
        history.setRemarks(remarks);
        return history;
    }
}
