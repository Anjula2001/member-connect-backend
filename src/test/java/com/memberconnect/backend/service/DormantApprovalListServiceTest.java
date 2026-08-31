package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.DormantApprovalListDTO;
import com.memberconnect.backend.dto.DormantMemberDecisionDTO;
import com.memberconnect.backend.enums.DormantApprovalListStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.event.DormantInactivationApprovedEvent;
import com.memberconnect.backend.model.DormantApprovalList;
import com.memberconnect.backend.model.DormantApprovalListMember;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.DormantApprovalListRepository;
import com.memberconnect.backend.repository.DormantConfigRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * The MMD17 board decision flow.
 *
 * The rule under test throughout is that recording a board meeting is all or
 * nothing. Before this rewrite the browser drove one call per member, so a
 * failure halfway left a list marked processed whose members were still pending -
 * and the per-member endpoint had no board-approval guard at all, which meant
 * every inactivation the product performed skipped the check the SRS requires.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DormantApprovalListServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private DormantConfigRepository configRepository;
    @Mock private DormantApprovalListRepository approvalListRepository;
    @Mock private BoardmeetingRepository boardMeetingRepository;
    @Mock private LoanObligationRepository obligationRepository;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private MemberStatusHistoryService memberStatusHistoryService;

    @InjectMocks private DormantMembershipService dormantService;

    private DormantApprovalList list;

    @BeforeEach
    void setUp() {
        list = listWith(
                member("M-1", MemberStatus.SENT_FOR_DORMANT_APPROVAL),
                member("M-2", MemberStatus.SENT_FOR_DORMANT_APPROVAL));

        when(approvalListRepository.findByListIdWithMembers("DAL-001")).thenReturn(Optional.of(list));
        when(approvalListRepository.save(any(DormantApprovalList.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(obligationRepository.findMemberIdsWithObligations(anyCollection())).thenReturn(List.of());
    }

    private long nextId = 1L;

    private Member member(String memberId, MemberStatus status) {
        Member member = new Member();
        ReflectionTestUtils.setField(member, "id", nextId++);
        member.setMemberId(memberId);
        member.setStatus(status);
        member.setSubmissionLocation("Gampaha");
        return member;
    }

    private DormantApprovalList listWith(Member... members) {
        DormantApprovalList entity = new DormantApprovalList();
        ReflectionTestUtils.setField(entity, "id", 100L);
        entity.setListId("DAL-001");
        entity.setStatus(DormantApprovalListStatus.CREATED);
        entity.setCreatedAt(LocalDateTime.now().minusDays(1));
        entity.setBoardMeetingDate(LocalDate.now().minusDays(1));

        for (Member member : members) {
            DormantApprovalListMember entry = new DormantApprovalListMember();
            entry.setApprovalList(entity);
            entry.setMember(member);
            entry.setMemberNo(member.getMemberId());
            entry.setPreviousStatus(MemberStatus.SELECTED_FOR_DORMANT);
            entity.getEntries().add(entry);
        }
        return entity;
    }

    private DormantMemberDecisionDTO decision(String memberId, String verdict, String reason) {
        DormantMemberDecisionDTO dto = new DormantMemberDecisionDTO();
        dto.setMemberId(memberId);
        dto.setDecision(verdict);
        dto.setRejectReason(reason);
        return dto;
    }

    private DormantApprovalListDTO payload(DormantMemberDecisionDTO... decisions) {
        DormantApprovalListDTO dto = new DormantApprovalListDTO();
        dto.setMemberDecisions(List.of(decisions));
        return dto;
    }

    // --------------------------------------------------------- happy path

    @Test
    void approvingAndRejectingInOneCallAppliesBothAndDerivesMixed() {
        DormantApprovalListDTO result = dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null),
                        decision("M-2", "Reject", "Paid in last week")));

        assertThat(list.memberList())
                .extracting(Member::getMemberId, Member::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("M-1", MemberStatus.INACTIVE_DORMANT),
                        // Back to where they were before the list, from previousStatus
                        org.assertj.core.groups.Tuple.tuple("M-2", MemberStatus.SELECTED_FOR_DORMANT));

        assertThat(result.getDecision()).isEqualTo("Mixed");
        assertThat(result.getApprovedCount()).isEqualTo(1);
        assertThat(result.getRejectedCount()).isEqualTo(1);
        assertThat(list.getStatus()).isEqualTo(DormantApprovalListStatus.PROCESSED);
    }

    @Test
    void theRejectionReasonIsStoredAgainstThatMemberAlone() {
        dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null),
                        decision("M-2", "Reject", "Paid in last week")));

        assertThat(list.getEntries())
                .extracting(DormantApprovalListMember::getMemberNo,
                        DormantApprovalListMember::getRejectReason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("M-1", null),
                        org.assertj.core.groups.Tuple.tuple("M-2", "Paid in last week"));
    }

    @Test
    void oneEventIsPublishedPerApprovedMemberAndNoneForRejected() {
        dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null),
                        decision("M-2", "Reject", "Still active")));

        verify(eventPublisher).publishEvent(
                new DormantInactivationApprovedEvent("M-1", "DAL-001"));
        verify(eventPublisher, never()).publishEvent(
                new DormantInactivationApprovedEvent("M-2", "DAL-001"));
    }

    @Test
    void inactivatedAtIsStampedOnlyWhenSomethingWasActuallyInactivated() {
        dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Reject", "no"), decision("M-2", "Reject", "no")));

        assertThat(list.getInactivatedAt())
                .as("nothing was inactivated, so there is no inactivation time")
                .isNull();
        assertThat(list.getDecision()).isEqualTo("Reject");
    }

    // ------------------------------------------------- validation is total

    /**
     * The single most important test here. A missing reason must abort the whole
     * meeting before any member is touched, not fail partway through leaving the
     * earlier members already inactivated.
     */
    @Test
    void aRejectionWithNoReasonRefusesAndWritesNothing() {
        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null),
                        decision("M-2", "Reject", "   "))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("rejection reason is required for M-2");

        verify(memberRepository, never()).save(any(Member.class));
        verify(approvalListRepository, never()).save(any(DormantApprovalList.class));
        assertThat(list.memberList())
                .allMatch(m -> m.getStatus() == MemberStatus.SENT_FOR_DORMANT_APPROVAL);
        assertThat(list.getStatus()).isEqualTo(DormantApprovalListStatus.CREATED);
    }

    @Test
    void everyMemberOnTheListNeedsADecision() {
        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No board decision supplied for: M-2");

        verify(memberRepository, never()).save(any(Member.class));
    }

    /**
     * The other direction: a stale browser tab must not be able to inactivate
     * somebody the board never saw.
     */
    @Test
    void aDecisionForSomebodyNotOnTheListIsRefused() {
        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null),
                        decision("M-2", "Approve", null),
                        decision("M-999", "Approve", null))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not part of approval list");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void twoConflictingVerdictsForOneMemberAreRefusedRatherThanLastWins() {
        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null),
                        decision("M-1", "Reject", "changed my mind"),
                        decision("M-2", "Approve", null))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Duplicate board decision supplied for M-1");
    }

    @Test
    void aVerdictThatIsNeitherApproveNorRejectIsRefused() {
        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Maybe", null), decision("M-2", "Approve", null))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be Approve or Reject");
    }

    @Test
    void anEmptyDecisionPayloadIsRefused() {
        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                new DormantApprovalListDTO()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A board decision is required for every member");
    }

    // ------------------------------------------------ processed is final

    @Test
    void aBoardMeetingIsRecordedOnlyOnce() {
        list.setStatus(DormantApprovalListStatus.PROCESSED);
        list.setProcessedAt(LocalDateTime.now().minusHours(1));
        list.setProcessedBy("Head Office User");

        assertThatThrownBy(() -> dormantService.processApprovalList("DAL-001",
                payload(decision("M-1", "Approve", null), decision("M-2", "Approve", null))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been processed");
    }

    /**
     * MMD18. The old code rolled back only members still pending and silently
     * left inactivated ones inactive with the list that authorised them deleted.
     */
    @Test
    void aProcessedListCannotBeDeleted() {
        list.setStatus(DormantApprovalListStatus.PROCESSED);
        list.setProcessedAt(LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> dormantService.deleteApprovalList("DAL-001"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("can no longer be deleted");

        verify(approvalListRepository, never()).delete(any(DormantApprovalList.class));
    }

    @Test
    void deletingAnUnprocessedListRestoresEachMemberToItsRecordedPriorStatus() {
        dormantService.deleteApprovalList("DAL-001");

        assertThat(list.memberList())
                .allMatch(m -> m.getStatus() == MemberStatus.SELECTED_FOR_DORMANT);
        verify(approvalListRepository).delete(list);
    }

    // ------------------------------------------------------------ list ids

    @Test
    void listIdsAreSequentialAndReadable() {
        when(approvalListRepository.findMaxListSequence()).thenReturn(7);
        when(boardMeetingRepository.findById(1L))
                .thenReturn(Optional.of(new com.memberconnect.backend.model.BoardMeeting()));
        Member candidate = member("M-9", MemberStatus.SELECTED_FOR_DORMANT);
        when(memberRepository.findByMemberId("M-9")).thenReturn(Optional.of(candidate));

        DormantApprovalListDTO dto = new DormantApprovalListDTO();
        dto.setBoardMeetingId(1L);
        dto.setMemberIds(List.of("M-9"));

        assertThat(dormantService.createApprovalList(dto).getListId()).isEqualTo("DAL-008");
    }
}
