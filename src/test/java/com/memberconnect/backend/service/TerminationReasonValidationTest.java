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
import com.memberconnect.backend.dto.TerminationMinorDisbursementDTO;
import com.memberconnect.backend.dto.TerminationReasonDTO;
import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.event.TerminationMarkedIncompleteEvent;
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
 * Unit tests for the MMT01 Termination Reasons Master (SRS Section 2).
 *
 * Plain Mockito, no Spring context and no database, matching
 * NotificationServiceTest - nothing here touches a real datasource.
 */
@ExtendWith(MockitoExtension.class)
class TerminationReasonValidationTest {

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

    @InjectMocks
    private TerminationService terminationService;

    private Member member;
    private TerminationReason resignation;
    private TerminationReason disciplinary;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setMemberId(MEMBER_ID);
        member.setStatus(MemberStatus.ACTIVE);

        resignation = reason(1L, "RESIGNATION", "Resignation from Post", true, 1);
        disciplinary = reason(2L, "DISCIPLINARY", "Disciplinary Action", true, 2);
    }

    // ---------------------------------------------------------------- create

    @Test
    void createStoresTheMasterNameAndIgnoresTheClientSuppliedReasonText() {
        stubNewRequestSave();
        when(terminationReasonRepository.findById(1L)).thenReturn(Optional.of(resignation));

        // The client claims a completely different reason text for master id 1.
        MemberTerminationRequestDTO dto = dto("1");
        dto.setTerminationReason("Fake Reason");

        TerminationRequestResponseDTO response = terminationService.saveRequest(MEMBER_ID, dto);

        assertThat(response.getTerminationReason()).isEqualTo("Resignation from Post");
        assertThat(response.getTerminationReasonId()).isEqualTo("1");
    }

    @Test
    void createLinksTheRequestToTheMasterRow() {
        stubNewRequestSave();
        when(terminationReasonRepository.findById(1L)).thenReturn(Optional.of(resignation));

        terminationService.saveRequest(MEMBER_ID, dto("1"));

        ArgumentCaptor<TerminationRequest> captor = ArgumentCaptor.forClass(TerminationRequest.class);
        verify(requestRepository).save(captor.capture());

        TerminationRequest saved = captor.getValue();
        assertThat(saved.getTerminationReasonRef()).isSameAs(resignation);
        assertThat(saved.getTerminationReason()).isEqualTo("Resignation from Post");
        assertThat(saved.getTerminationReasonId()).isEqualTo("1");
    }

    @Test
    void createRejectsAnUnknownReasonId() {
        stubNewRequestLookups();
        when(terminationReasonRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto("999")))
                .hasMessage("Invalid termination reason");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRejectsAReasonIdThatIsNotANumber() {
        stubNewRequestLookups();

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto("Resignation from Post")))
                .hasMessage("Invalid termination reason");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void createStillRejectsAMissingReason() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto("")))
                .hasMessage("Termination reason is required");
    }

    @Test
    void createRejectsAnInactiveReason() {
        TerminationReason retired = reason(3L, "TRANSFER", "Transfer to Another Organization", false, 3);

        stubNewRequestLookups();
        when(terminationReasonRepository.findById(3L)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto("3")))
                .hasMessageContaining("no longer available");

        verify(requestRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateKeepsAnInactiveReasonTheRequestIsAlreadyUsing() {
        TerminationReason retired = reason(2L, "DISCIPLINARY", "Disciplinary Action", false, 2);

        TerminationRequest existing = existingRequest();
        existing.setTerminationReasonRef(retired);

        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existing));
        when(terminationReasonRepository.findById(2L)).thenReturn(Optional.of(retired));
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        TerminationRequestResponseDTO response =
                terminationService.updateRequest(REQUEST_NO, dto("2"));

        assertThat(response.getTerminationReason()).isEqualTo("Disciplinary Action");
        assertThat(existing.getTerminationReasonRef()).isSameAs(retired);
    }

    @Test
    void updateRejectsSwitchingToADifferentInactiveReason() {
        TerminationReason otherRetired = reason(4L, "OTHER", "Other", false, 4);

        TerminationRequest existing = existingRequest();
        existing.setTerminationReasonRef(resignation);

        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existing));
        when(terminationReasonRepository.findById(4L)).thenReturn(Optional.of(otherRetired));

        assertThatThrownBy(() -> terminationService.updateRequest(REQUEST_NO, dto("4")))
                .hasMessageContaining("no longer available");

        verify(requestRepository, never()).save(any());
    }

    @Test
    void updateAllowsSwitchingToAnActiveReason() {
        TerminationRequest existing = existingRequest();
        existing.setTerminationReasonRef(resignation);

        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existing));
        when(terminationReasonRepository.findById(2L)).thenReturn(Optional.of(disciplinary));
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        TerminationRequestResponseDTO response =
                terminationService.updateRequest(REQUEST_NO, dto("2"));

        assertThat(response.getTerminationReason()).isEqualTo("Disciplinary Action");
        assertThat(response.getTerminationReasonId()).isEqualTo("2");
    }

    // --------------------------------------------------------- master lookup

    @Test
    void masterOptionsReturnOnlyActiveReasonsInDisplayOrder() {
        when(terminationReasonRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(resignation, disciplinary));

        List<TerminationReasonDTO> options = terminationService.getTerminationReasonOptions();

        assertThat(options).extracting(TerminationReasonDTO::getName)
                .containsExactly("Resignation from Post", "Disciplinary Action");
        assertThat(options).extracting(TerminationReasonDTO::getId).containsExactly(1L, 2L);
        assertThat(options).extracting(TerminationReasonDTO::getCode)
                .containsExactly("RESIGNATION", "DISCIPLINARY");

        // Retired reasons are filtered out by the query itself, never in the caller.
        verify(terminationReasonRepository).findByActiveTrueOrderByDisplayOrderAsc();
        verify(terminationReasonRepository, never()).findAll();
    }

    // ------------------------------------------------------- legacy requests

    @Test
    void requestsSavedBeforeTheMasterExistedStillLoadWithTheirStoredReason() {
        TerminationRequest legacy = existingRequest();
        legacy.setTerminationReasonId("3");
        legacy.setTerminationReason("Transfer to Another Organization");
        legacy.setTerminationReasonRef(null);

        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(legacy));

        List<TerminationRequestResponseDTO> responses =
                terminationService.getRequestsByMember(MEMBER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTerminationReason())
                .isEqualTo("Transfer to Another Organization");
        assertThat(responses.get(0).getTerminationReasonId()).isEqualTo("3");

        // Reading an existing request must not consult the master at all.
        verify(terminationReasonRepository, never()).findById(any());
    }

    // ----------------------------------------- untouched neighbouring MMT01 behaviour

    @Test
    void minorDisbursementValidationStillRejectsAnAccountTheMemberDoesNotOwn() {
        stubNewRequestLookups();
        when(terminationReasonRepository.findById(1L)).thenReturn(Optional.of(resignation));
        when(minorSavingsAccountRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());

        MemberTerminationRequestDTO dto = dto("1");
        TerminationMinorDisbursementDTO item = new TerminationMinorDisbursementDTO();
        item.setMinorAccountNo("MS-999");
        dto.setMinorDisbursements(List.of(item));

        assertThatThrownBy(() -> terminationService.saveRequest(MEMBER_ID, dto))
                .hasMessageContaining("Invalid minor savings account number");
    }

    @Test
    void documentLockingStillBlocksSubmittedRequests() {
        TerminationRequest submitted = existingRequest();
        submitted.setStatus(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);

        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> terminationService.assertDocumentsEditable(REQUEST_NO))
                .hasMessageContaining("Cannot modify documents");
    }

    @Test
    void documentLockingStillAllowsNewRequests() {
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existingRequest()));

        assertThatCode(() -> terminationService.assertDocumentsEditable(REQUEST_NO))
                .doesNotThrowAnyException();
    }

    @Test
    void markIncompleteStillPublishesTheNotificationEvent() {
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(existingRequest()));
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        terminationService.markIncomplete(REQUEST_NO, "Bank passbook copy is not clear");

        verify(eventPublisher).publishEvent(any(TerminationMarkedIncompleteEvent.class));
    }

    // ---------------------------------------------------------------- helpers

    private void stubNewRequestLookups() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));
        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(requestRepository.findLastRequestByPrefix(any())).thenReturn(Optional.empty());
    }

    private void stubNewRequestSave() {
        stubNewRequestLookups();
        when(requestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private MemberTerminationRequestDTO dto(String reasonId) {
        MemberTerminationRequestDTO dto = new MemberTerminationRequestDTO();
        dto.setTerminationReasonId(reasonId);
        dto.setRequestedDate(LocalDate.now().toString());
        dto.setEffectiveDate(LocalDate.now().toString());
        dto.setComment("");
        return dto;
    }

    private TerminationRequest existingRequest() {
        TerminationRequest request = new TerminationRequest();
        request.setRequestNo(REQUEST_NO);
        request.setMemberId(MEMBER_ID);
        request.setStatus(TerminationRequestStatus.NEW);
        request.setRequestedDate(LocalDate.now());
        request.setEffectiveDate(LocalDate.now());
        request.setTerminationReasonId("1");
        request.setTerminationReason("Resignation from Post");
        return request;
    }

    private TerminationReason reason(Long id, String code, String name, boolean active, int order) {
        TerminationReason reason = new TerminationReason();
        reason.setId(id);
        reason.setCode(code);
        reason.setName(name);
        reason.setActive(active);
        reason.setDisplayOrder(order);
        return reason;
    }
}
