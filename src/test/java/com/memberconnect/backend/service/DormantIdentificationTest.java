package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.DormantApprovalListRepository;
import com.memberconnect.backend.repository.DormantConfigRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * MMD10 - who the identification process flags, and who it lets go.
 *
 * The case that drove most of this: a member with no lastActivityDate at all.
 * Both loops used to guard on the field being non-null, so the most dormant
 * member imaginable - one with nothing ever recorded - was silently exempt.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DormantIdentificationTest {

    @Mock private MemberRepository memberRepository;
    @Mock private DormantConfigRepository configRepository;
    @Mock private DormantApprovalListRepository approvalListRepository;
    @Mock private BoardmeetingRepository boardMeetingRepository;
    @Mock private LoanObligationRepository obligationRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private DormantMembershipService dormantService;

    private DormantConfig config;

    @BeforeEach
    void setUp() {
        config = new DormantConfig();
        config.setDormantPeriodMonths(12);
        config.setEnabled(true);
        when(configRepository.findAll()).thenReturn(List.of(config));
        when(configRepository.save(any(DormantConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findByStatusIn(anyList())).thenReturn(List.of());
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of());
    }

    private Member active(String memberId, LocalDate lastActivity, LocalDate membershipStart) {
        Member member = new Member();
        member.setMemberId(memberId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setLastActivityDate(lastActivity);
        member.setMembershipStartDate(membershipStart);
        return member;
    }

    private Member flagged(String memberId, LocalDate lastActivity) {
        Member member = new Member();
        member.setMemberId(memberId);
        member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
        member.setDormantSelectionDate(LocalDate.now().minusMonths(1));
        member.setLastActivityDate(lastActivity);
        return member;
    }

    @Test
    void aMemberInactiveBeyondTheCutoffIsFlagged() {
        Member stale = active("M-1", LocalDate.now().minusMonths(14), null);
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of(stale));

        assertThat(dormantService.runIdentification()).containsExactly(1, 0);
        assertThat(stale.getStatus()).isEqualTo(MemberStatus.SELECTED_FOR_DORMANT);
        assertThat(stale.getDormantSelectionDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void aMemberActiveInsideTheCutoffIsLeftAlone() {
        Member recent = active("M-1", LocalDate.now().minusMonths(2), null);
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of(recent));

        assertThat(dormantService.runIdentification()).containsExactly(0, 0);
        assertThat(recent.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    /**
     * The regression this whole change exists for. Before the fix a null
     * lastActivityDate meant "skip", so this member could never be flagged.
     */
    @Test
    void aMemberWithNoActivityEverIsFlaggedUsingTheirMembershipStartDate() {
        Member never = active("M-1", null, LocalDate.now().minusYears(5));
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of(never));

        assertThat(dormantService.runIdentification()).containsExactly(1, 0);
        assertThat(never.getStatus()).isEqualTo(MemberStatus.SELECTED_FOR_DORMANT);
    }

    @Test
    void aRecentlyJoinedMemberWithNoActivityYetIsNotFlagged() {
        Member fresh = active("M-1", null, LocalDate.now().minusMonths(1));
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of(fresh));

        assertThat(dormantService.runIdentification()).containsExactly(0, 0);
        assertThat(fresh.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void aFlaggedMemberWhoBecameActiveAgainIsCleared() {
        Member revived = flagged("M-1", LocalDate.now().minusDays(3));
        when(memberRepository.findByStatusIn(anyList())).thenReturn(List.of(revived));

        assertThat(dormantService.runIdentification()).containsExactly(0, 1);
        assertThat(revived.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(revived.getDormantSelectionDate()).isNull();
    }

    @Test
    void aFlaggedMemberStillInactiveStaysFlagged() {
        Member stillStale = flagged("M-1", LocalDate.now().minusMonths(20));
        when(memberRepository.findByStatusIn(anyList())).thenReturn(List.of(stillStale));

        assertThat(dormantService.runIdentification()).containsExactly(0, 0);
        assertThat(stillStale.getStatus()).isEqualTo(MemberStatus.SELECTED_FOR_DORMANT);
    }

    @Test
    void theCutoffFollowsTheConfiguredPeriod() {
        config.setDormantPeriodMonths(3);
        Member fourMonths = active("M-1", LocalDate.now().minusMonths(4), null);
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of(fourMonths));

        assertThat(dormantService.runIdentification()).containsExactly(1, 0);
    }

    /** Without this the scheduler cannot tell a missed run from a completed one. */
    @Test
    void theRunStampsWhenItLastHappened() {
        dormantService.runIdentification();

        assertThat(config.getLastRunOn()).isEqualTo(LocalDate.now());
        assertThat(config.getLastRunSelectedCount()).isZero();
        assertThat(config.getLastRunClearedCount()).isZero();
    }
}
