package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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

import com.memberconnect.backend.dto.MemberTerminationRequestDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.TerminationReason;
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
 * The MMT01 date rules on a termination request, which had no coverage at all.
 *
 * Effective Date is bounded on both sides - it may not precede the Requested
 * Date, and it may not run ahead of today - and both bounds are asserted against
 * saveRequest and updateRequest, because the two used to carry duplicate copies
 * of the check and are now expected to share validateRequestDates().
 *
 * Plain Mockito, no Spring context and no database, matching
 * TerminationReasonValidationTest.
 */
@ExtendWith(MockitoExtension.class)
class TerminationDateValidationTest {

    private static final String MEMBER_ID = "M-001";
    private static final String REQUEST_NO = "T-2026-001";

    private static final String EFFECTIVE_BEFORE_REQUESTED =
            "Effective Date cannot be before the Requested Date";
    private static final String EFFECTIVE_IN_FUTURE =
            "Effective Date cannot be a future date";
    private static final String REQUESTED_IN_FUTURE =
            "Requested Date cannot be a future date";

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

    @InjectMocks
    private TerminationService terminationService;

    private Member member;
    private TerminationReason resignation;

    private LocalDate today;
    private LocalDate yesterday;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setMemberId(MEMBER_ID);
        member.setStatus(MemberStatus.ACTIVE);

        resignation = reason(1L, "RESIGNATION", "Resignation from Post");

        // Relative rather than fixed, so these tests do not start failing on a
        // date somebody picked when they were written.
        today = LocalDate.now();
        yesterday = today.minusDays(1);
        tomorrow = today.plusDays(1);
    }

    // ------------------------------------------------------------ saveRequest

    @Test
    void saveRejectsAnEffectiveDateBeforeTheRequestedDate() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto(today, yesterday)))
                .hasMessage(EFFECTIVE_BEFORE_REQUESTED);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void saveAcceptsAnEffectiveDateOnTheRequestedDate() {
        stubNewRequestSave();

        assertThatCode(() -> terminationService.saveRequest(MEMBER_ID, dto(today, today)))
                .doesNotThrowAnyException();

        assertThat(savedRequest().getEffectiveDate()).isEqualTo(today);
    }

    @Test
    void saveAcceptsAnEffectiveDateAfterTheRequestedDate() {
        stubNewRequestSave();

        LocalDate requested = today.minusDays(5);
        LocalDate effective = today.minusDays(2);

        assertThatCode(() -> terminationService.saveRequest(MEMBER_ID, dto(requested, effective)))
                .doesNotThrowAnyException();

        TerminationRequest saved = savedRequest();
        assertThat(saved.getRequestedDate()).isEqualTo(requested);
        assertThat(saved.getEffectiveDate()).isEqualTo(effective);
    }

    @Test
    void saveStillRejectsAFutureEffectiveDate() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto(today, tomorrow)))
                .hasMessage(EFFECTIVE_IN_FUTURE);

        verify(requestRepository, never()).save(any());
    }

    /**
     * A future Requested Date also puts the Effective Date behind it, so both
     * bounds are broken at once. The Requested Date is the one reported: it is
     * the field the user actually has to fix, and correcting it resolves the
     * ordering complaint too.
     */
    @Test
    void saveReportsTheFutureRequestedDateRatherThanTheOrderingItCauses() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto(tomorrow, today)))
                .hasMessage(REQUESTED_IN_FUTURE);

        verify(requestRepository, never()).save(any());
    }

    // ---------------------------------------------------------- updateRequest

    @Test
    void updateRejectsAnEffectiveDateBeforeTheRequestedDate() {
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existingRequest()));

        assertThatThrownBy(() -> terminationService.updateRequest(REQUEST_NO, dto(today, yesterday)))
                .hasMessage(EFFECTIVE_BEFORE_REQUESTED);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void updateStillRejectsAFutureEffectiveDate() {
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existingRequest()));

        assertThatThrownBy(() -> terminationService.updateRequest(REQUEST_NO, dto(today, tomorrow)))
                .hasMessage(EFFECTIVE_IN_FUTURE);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void updateAcceptsAnEffectiveDateOnOrAfterTheRequestedDate() {
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existingRequest()));
        when(terminationReasonRepository.findById(1L)).thenReturn(Optional.of(resignation));
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        LocalDate requested = today.minusDays(3);

        assertThatCode(() -> terminationService.updateRequest(REQUEST_NO, dto(requested, today)))
                .doesNotThrowAnyException();

        TerminationRequest saved = savedRequest();
        assertThat(saved.getRequestedDate()).isEqualTo(requested);
        assertThat(saved.getEffectiveDate()).isEqualTo(today);
    }

    // ----------------------------------------------------------------- helpers

    private void stubNewRequestSave() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(requestRepository.findLastRequestByPrefix(any())).thenReturn(Optional.empty());
        when(terminationReasonRepository.findById(1L)).thenReturn(Optional.of(resignation));
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private TerminationRequest savedRequest() {
        ArgumentCaptor<TerminationRequest> captor = ArgumentCaptor.forClass(TerminationRequest.class);
        verify(requestRepository).save(captor.capture());
        return captor.getValue();
    }

    private MemberTerminationRequestDTO dto(LocalDate requestedDate, LocalDate effectiveDate) {
        MemberTerminationRequestDTO dto = new MemberTerminationRequestDTO();
        dto.setTerminationReasonId("1");
        dto.setRequestedDate(requestedDate.toString());
        dto.setEffectiveDate(effectiveDate.toString());
        dto.setComment("");
        return dto;
    }

    private TerminationRequest existingRequest() {
        TerminationRequest request = new TerminationRequest();
        request.setRequestNo(REQUEST_NO);
        request.setMemberId(MEMBER_ID);
        request.setStatus(TerminationRequestStatus.NEW);
        request.setRequestedDate(today);
        request.setEffectiveDate(today);
        request.setTerminationReasonId("1");
        request.setTerminationReason("Resignation from Post");
        return request;
    }

    private TerminationReason reason(Long id, String code, String name) {
        TerminationReason reason = new TerminationReason();
        reason.setId(id);
        reason.setCode(code);
        reason.setName(name);
        reason.setActive(true);
        reason.setDisplayOrder(1);
        return reason;
    }
}
