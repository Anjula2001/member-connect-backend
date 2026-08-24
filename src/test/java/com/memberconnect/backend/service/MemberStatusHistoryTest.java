package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import com.memberconnect.backend.config.MemberStatusHistoryBackfillRunner;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberStatusHistory;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MemberStatusHistoryRepository;
import com.memberconnect.backend.repository.RetirementRequestRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

/**
 * The question this history exists to answer is "was the member Active on the exam's
 * last date", and both halves of that are tested here: what statusOn reports, and what
 * the backfill puts in front of it for members whose status changed before the table
 * existed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberStatusHistoryTest {

    private static final LocalDate EXAM_LAST_DATE = LocalDate.of(2023, 8, 15);

    @Mock private MemberStatusHistoryRepository historyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private TerminationRequestRepository terminationRepository;
    @Mock private RetirementRequestRepository retirementRepository;
    @Mock private MemberDeathRecordRepository deathRecordRepository;

    @InjectMocks private MemberStatusHistoryService historyService;

    private MemberStatusHistoryBackfillRunner backfillRunner() {
        return new MemberStatusHistoryBackfillRunner(
                memberRepository, historyRepository, terminationRepository,
                retirementRepository, deathRecordRepository);
    }

    // ── statusOn ─────────────────────────────────────────────────────────────

    @Test
    void statusOnReportsTheStatusInForceOnThatDate() {
        when(historyRepository.findStatusAsAt(eq("M-001"), eq(EXAM_LAST_DATE), any(Pageable.class)))
                .thenReturn(List.of(historyRow(MemberStatus.ACTIVE, MemberStatus.TERMINATION_APPROVED,
                        LocalDate.of(2023, 1, 1))));

        assertThat(historyService.statusOn("M-001", EXAM_LAST_DATE))
                .isEqualTo(MemberStatus.TERMINATION_APPROVED);
        assertThat(historyService.wasNotActiveOn("M-001", EXAM_LAST_DATE)).isTrue();
    }

    /**
     * The case the whole feature turns on: inactive when the exam was sat, active again
     * by the time the request is raised. The query is bounded by the exam date, so the
     * later return to ACTIVE is not part of the answer.
     */
    @Test
    void aMemberActiveAgainNowIsStillJudgedOnTheStatusHeldAtTheExam() {
        when(historyRepository.findStatusAsAt(eq("M-002"), eq(EXAM_LAST_DATE), any(Pageable.class)))
                .thenReturn(List.of(historyRow(MemberStatus.ACTIVE, MemberStatus.INACTIVE,
                        LocalDate.of(2022, 6, 1))));

        assertThat(historyService.wasNotActiveOn("M-002", EXAM_LAST_DATE)).isTrue();
    }

    /** Going inactive after the exam does not disqualify the member. */
    @Test
    void inactivityRecordedAfterTheExamDoesNotCount() {
        // Nothing on or before the exam date but the ACTIVE anchor
        when(historyRepository.findStatusAsAt(eq("M-003"), eq(EXAM_LAST_DATE), any(Pageable.class)))
                .thenReturn(List.of(historyRow(null, MemberStatus.ACTIVE, LocalDate.of(2015, 1, 1))));

        assertThat(historyService.wasNotActiveOn("M-003", EXAM_LAST_DATE)).isFalse();
    }

    @Test
    void anUnrecordedHistoryIsNotEvidenceOfInactivity() {
        when(historyRepository.findStatusAsAt(eq("M-004"), eq(EXAM_LAST_DATE), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(historyService.statusOn("M-004", EXAM_LAST_DATE)).isNull();
        assertThat(historyService.wasNotActiveOn("M-004", EXAM_LAST_DATE)).isFalse();
    }

    @Test
    void aTransitionToTheSameStatusIsNotRecorded() {
        Member member = member("M-005", MemberStatus.ACTIVE, LocalDate.of(2015, 1, 1));
        member.setId(5L);

        historyService.record(member, MemberStatus.ACTIVE, MemberStatus.ACTIVE, null, "TEST");

        verify(historyRepository, never()).save(any(MemberStatusHistory.class));
    }

    // ── backfill ─────────────────────────────────────────────────────────────

    @Test
    void theBackfillLeavesATableThatAlreadyHasRowsAlone() {
        when(historyRepository.count()).thenReturn(1L);

        backfillRunner().run();

        verify(memberRepository, never()).findAll();
        verify(historyRepository, never()).saveAll(any());
    }

    @Test
    void anActiveMemberGetsAnAnchorAtTheirMembershipStart() {
        Member member = member("M-010", MemberStatus.ACTIVE, LocalDate.of(2016, 4, 1));
        stubBackfillSources(List.of(member), List.of());

        List<MemberStatusHistory> saved = runBackfill();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getToStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(saved.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2016, 4, 1));
        assertThat(saved.get(0).getSource()).isEqualTo("BACKFILL_MEMBERSHIP_START");
    }

    @Test
    void aTerminatedMemberIsDatedFromTheTerminationsEffectiveDate() {
        Member member = member("M-011", MemberStatus.TERMINATED, LocalDate.of(2016, 4, 1));
        TerminationRequest request = termination("M-011", TerminationRequestStatus.APPROVED,
                LocalDate.of(2022, 1, 10), LocalDate.of(2022, 3, 31));
        stubBackfillSources(List.of(member), List.of(request));

        List<MemberStatusHistory> saved = runBackfill();

        assertThat(saved).hasSize(2);
        MemberStatusHistory episode = saved.get(1);
        assertThat(episode.getToStatus()).isEqualTo(MemberStatus.TERMINATED);
        // The effective date, not the requested date
        assertThat(episode.getEffectiveDate()).isEqualTo(LocalDate.of(2022, 3, 31));
    }

    /**
     * INACTIVE is set directly, with no request behind it to date. Writing the period
     * with a guessed start would refuse scholarships for exams the member was active
     * for, so only the anchor is written and the member reads as "not known" after it.
     */
    @Test
    void aMemberInactiveWithNothingToDateItGetsNoPeriodRow() {
        Member member = member("M-012", MemberStatus.INACTIVE, LocalDate.of(2016, 4, 1));
        stubBackfillSources(List.of(member), List.of());

        List<MemberStatusHistory> saved = runBackfill();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSource()).isEqualTo("BACKFILL_MEMBERSHIP_START");
    }

    /**
     * A membership start on or after the date the member left ACTIVE means one of the
     * two is wrong. Keeping the anchor would sort it last and report the member as
     * active ever since - the one outcome that must not happen.
     */
    @Test
    void anAnchorThatDoesNotPrecedeThePeriodIsDropped() {
        Member member = member("M-013", MemberStatus.TERMINATED, LocalDate.of(2022, 6, 1));
        TerminationRequest request = termination("M-013", TerminationRequestStatus.APPROVED,
                LocalDate.of(2022, 1, 10), LocalDate.of(2022, 3, 31));
        stubBackfillSources(List.of(member), List.of(request));

        List<MemberStatusHistory> saved = runBackfill();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSource()).isEqualTo("BACKFILL_CURRENT_STATUS");
        assertThat(saved.get(0).getToStatus()).isEqualTo(MemberStatus.TERMINATED);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubBackfillSources(List<Member> members, List<TerminationRequest> terminations) {
        when(historyRepository.count()).thenReturn(0L);
        when(memberRepository.findAll()).thenReturn(members);
        when(terminationRepository.findAll()).thenReturn(terminations);
        when(retirementRepository.findAll()).thenReturn(List.of());
        when(deathRecordRepository.findAllWithMember()).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<MemberStatusHistory> runBackfill() {
        backfillRunner().run();

        ArgumentCaptor<List<MemberStatusHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(historyRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Member member(String memberId, MemberStatus status, LocalDate membershipStart) {
        Member member = new Member();
        member.setMemberId(memberId);
        member.setStatus(status);
        member.setMembershipStartDate(membershipStart);
        return member;
    }

    private TerminationRequest termination(
            String memberId, TerminationRequestStatus status,
            LocalDate requestedDate, LocalDate effectiveDate) {
        TerminationRequest request = new TerminationRequest();
        request.setMemberId(memberId);
        request.setStatus(status);
        request.setRequestedDate(requestedDate);
        request.setEffectiveDate(effectiveDate);
        return request;
    }

    private MemberStatusHistory historyRow(MemberStatus from, MemberStatus to, LocalDate effectiveDate) {
        MemberStatusHistory row = new MemberStatusHistory();
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setEffectiveDate(effectiveDate);
        return row;
    }
}
