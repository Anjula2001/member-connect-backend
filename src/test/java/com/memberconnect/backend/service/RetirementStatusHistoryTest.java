package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.MemberRetirementRequestDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.RetirementRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.RetirementRequest;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.RetirementRequestRepository;

/**
 * Raising a retirement request is a member status change, not just a new row in
 * retirement_request: the member leaves ACTIVE for RETIREMENT_REQUESTED and
 * member_status_history gains the row that dates the move.
 *
 * The history row is what later answers "what was this member on date X" - the
 * Grade 5 and University eligibility checks read it - so losing it would not fail
 * anything here, it would quietly change the answer to a question asked much later.
 */
@ExtendWith(MockitoExtension.class)
class RetirementStatusHistoryTest {

    private static final String MEMBER_ID = "M-001";
    private static final LocalDate REQUESTED_DATE = LocalDate.now().minusDays(1);

    @Mock private MemberRepository memberRepository;
    @Mock private RetirementRequestRepository requestRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanObligationRepository obligationRepository;
    @Mock private DocumentService documentService;
    @Mock private CurrentUserService currentUserService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MemberStatusHistoryService memberStatusHistoryService;

    @InjectMocks private RetirementService retirementService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setId(1L);
        member.setMemberId(MEMBER_ID);
        member.setStatus(MemberStatus.ACTIVE);
    }

    private MemberRetirementRequestDTO dto() {
        MemberRetirementRequestDTO dto = new MemberRetirementRequestDTO();
        dto.setRequestedDate(REQUESTED_DATE.toString());
        dto.setEffectiveDate(REQUESTED_DATE.toString());
        return dto;
    }

    private void stubNewRequest() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(requestRepository.findLastRequestByPrefix(anyString())).thenReturn(Optional.empty());
        when(requestRepository.save(any(RetirementRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void creatingARequestMovesTheMemberOutOfActive() {
        stubNewRequest();

        retirementService.saveRequest(MEMBER_ID, dto());

        assertThat(member.getStatus()).isEqualTo(MemberStatus.RETIREMENT_REQUESTED);
        verify(memberRepository).save(member);
    }

    @Test
    void creatingARequestRecordsTheMoveInMemberStatusHistory() {
        stubNewRequest();

        String requestNo = retirementService.saveRequest(MEMBER_ID, dto()).getRequestNo();

        // Dated by the requested date rather than today: the row has to say when the
        // member actually left ACTIVE, not when the clerk happened to key it in.
        verify(memberStatusHistoryService).record(
                member,
                MemberStatus.ACTIVE,
                MemberStatus.RETIREMENT_REQUESTED,
                REQUESTED_DATE,
                "RETIREMENT_REQUESTED",
                "Retirement request " + requestNo);
    }

    @Test
    void theRequestItselfStartsAtNew() {
        stubNewRequest();

        assertThat(retirementService.saveRequest(MEMBER_ID, dto()).getStatus())
                .isEqualTo(RetirementRequestStatus.NEW.name());
    }
}
