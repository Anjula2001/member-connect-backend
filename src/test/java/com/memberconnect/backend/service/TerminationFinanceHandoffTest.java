package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.event.TerminationApprovedEvent;
import com.memberconnect.backend.event.TerminationCompletedEvent;
import com.memberconnect.backend.event.TerminationRejectedEvent;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import com.memberconnect.backend.repository.TerminationMinorDisbursementRepository;
import com.memberconnect.backend.repository.TerminationReasonRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

/**
 * Tests the MMT09 -> MMT11 handover: what the board's approval actually does to
 * the member, and what the Finance Module's callback is allowed to do afterwards.
 *
 * The distinction under test throughout is that board approval stops the
 * remittance (TERMINATION_APPROVED) while only Finance closes the membership
 * (TERMINATED). Plain Mockito, no Spring context and no database.
 */
@ExtendWith(MockitoExtension.class)
class TerminationFinanceHandoffTest {

    private static final String MEMBER_ID = "M-001";
    private static final String REQUEST_NO = "T-2026-001";

    @Mock private MemberRepository memberRepository;
    @Mock private TerminationRequestRepository requestRepository;
    @Mock private TerminationReasonRepository terminationReasonRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanObligationRepository obligationRepository;
    @Mock private DocumentService documentService;
    @Mock private MinorSavingsAccountRepository minorSavingsAccountRepository;
    @Mock private TerminationMinorDisbursementRepository minorDisbursementRepository;
    @Mock private BankRepository bankRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private AuditService auditService;


    @InjectMocks private TerminationService terminationService;

    private Member member;
    private TerminationRequest request;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setMemberId(MEMBER_ID);

        request = new TerminationRequest();
        request.setRequestNo(REQUEST_NO);
        request.setMemberId(MEMBER_ID);
    }

    private void requestExists(TerminationRequestStatus status) {
        request.setStatus(status);
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(request));
    }

    private void memberExists(MemberStatus status) {
        member.setStatus(status);
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    private void requestSaves() {
        when(requestRepository.save(any(TerminationRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------- approval

    @Test
    void boardApprovalStopsTheRemittanceWithoutClosingTheMembership() {
        requestExists(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
        memberExists(MemberStatus.TERMINATION_REQUESTED);
        requestSaves();

        terminationService.approveRequest(REQUEST_NO);

        // The SRS reserves TERMINATED for the Finance Module: approval alone must
        // not claim that the savings accounts have been cleared.
        assertThat(member.getStatus()).isEqualTo(MemberStatus.TERMINATION_APPROVED);
        assertThat(request.getStatus()).isEqualTo(TerminationRequestStatus.APPROVED);
    }

    @Test
    void boardApprovalHandsTheMemberToFinance() {
        requestExists(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
        memberExists(MemberStatus.TERMINATION_REQUESTED);
        requestSaves();

        terminationService.approveRequest(REQUEST_NO);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue())
                .isInstanceOf(TerminationApprovedEvent.class)
                .isEqualTo(new TerminationApprovedEvent(MEMBER_ID, REQUEST_NO));
    }

    @Test
    void rejectionReturnsTheMemberToActiveAndTellsThem() {
        requestExists(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
        memberExists(MemberStatus.TERMINATION_REQUESTED);
        requestSaves();

        terminationService.rejectRequest(REQUEST_NO, "Loan not settled");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(request.getStatus()).isEqualTo(TerminationRequestStatus.REJECTED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue())
                .isEqualTo(new TerminationRejectedEvent(MEMBER_ID, REQUEST_NO, "Loan not settled"));
    }

    // ----------------------------------------------------------- completion

    @Test
    void financeCompletionClosesTheMembership() {
        requestExists(TerminationRequestStatus.APPROVED);
        memberExists(MemberStatus.TERMINATION_APPROVED);

        terminationService.completeTermination(REQUEST_NO);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.TERMINATED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue())
                .isEqualTo(new TerminationCompletedEvent(MEMBER_ID, REQUEST_NO));
    }

    @Test
    void financeCompletionIsSafeToRetry() {
        requestExists(TerminationRequestStatus.APPROVED);
        memberExists(MemberStatus.TERMINATED);

        terminationService.completeTermination(REQUEST_NO);

        // Finance is an external caller and will retry. The second call must not
        // fail, and must not tell the member their membership ended twice.
        assertThat(member.getStatus()).isEqualTo(MemberStatus.TERMINATED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void financeCannotCompleteARequestTheBoardNeverApproved() {
        requestExists(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);

        assertThatThrownBy(() -> terminationService.completeTermination(REQUEST_NO))
                .hasMessageContaining("cannot be completed from status SUBMITTED_FOR_APPROVAL");

        verify(memberRepository, never()).save(any(Member.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void financeCannotCompleteAMemberWhoIsNotAwaitingIt() {
        requestExists(TerminationRequestStatus.APPROVED);
        memberExists(MemberStatus.ACTIVE);

        assertThatThrownBy(() -> terminationService.completeTermination(REQUEST_NO))
                .hasMessageContaining("Expected TERMINATION_APPROVED");

        verify(memberRepository, never()).save(any(Member.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void financeCompletionRejectsAnUnknownRequest() {
        when(requestRepository.findByRequestNo("T-NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> terminationService.completeTermination("T-NOPE"))
                .hasMessageContaining("Termination request not found: T-NOPE");
    }

    // -------------------------------------------------------------- handoff

    @Test
    void handoffCarriesWhatFinanceNeedsToCloseTheAccounts() {
        member.setNameWithInitials("A. B. Perera");
        member.setNic("199012345678");
        request.setStatus(TerminationRequestStatus.APPROVED);
        request.setEffectiveDate(java.time.LocalDate.of(2026, 8, 1));
        request.setTerminationReason("Resignation from Post");
        request.setMinorDisbursements(List.of());

        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(request));
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));

        var handoff = terminationService.buildFinanceHandoff(REQUEST_NO);

        assertThat(handoff.getRequestNo()).isEqualTo(REQUEST_NO);
        assertThat(handoff.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(handoff.getMemberName()).isEqualTo("A. B. Perera");
        assertThat(handoff.getNic()).isEqualTo("199012345678");
        assertThat(handoff.getEffectiveDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 1));
        assertThat(handoff.getTerminationReason()).isEqualTo("Resignation from Post");
        assertThat(handoff.getMinorDisbursements()).isEmpty();
    }
}
