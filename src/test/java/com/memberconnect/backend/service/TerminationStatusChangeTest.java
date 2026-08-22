package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.model.User;
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
 * Unit tests for the MMT04 status-change matrix (SRS section 2.2.4) and the
 * "Inactive rights" the spec attaches to it.
 *
 * These tests populate the SecurityContext directly because the rights check
 * reads the authenticated principal, exactly as MemberApplicationService does.
 */
@ExtendWith(MockitoExtension.class)
class TerminationStatusChangeTest {

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
        member.setStatus(MemberStatus.TERMINATION_REQUESTED);

        request = new TerminationRequest();
        request.setRequestNo(REQUEST_NO);
        request.setMemberId(MEMBER_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(Role role) {
        User user = new User();
        user.setUsername("tester");
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    private void requestIs(TerminationRequestStatus status) {
        request.setStatus(status);
        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(request));
    }

    private void memberFound() {
        when(memberRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    private void requestSaves() {
        when(requestRepository.save(any(TerminationRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void onlyRequestForMember() {
        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(request));
    }

    // ------------------------------------------------- the permitted matrix

    @ParameterizedTest(name = "{0} -> {1} is permitted")
    @CsvSource({
            "NEW, INACTIVE",
            "INCOMPLETE, NEW",
            "INCOMPLETE, INACTIVE",
            "SUBMITTED_FOR_APPROVAL, NEW",
            "SUBMITTED_FOR_APPROVAL, INACTIVE",
            "REJECTED, NEW",
            "REJECTED, INACTIVE",
            "INACTIVE, NEW"
    })
    void permitsEveryTransitionInTheSrsTable(String from, String to) {
        signInAs(Role.HEAD_OFFICE);
        requestIs(TerminationRequestStatus.valueOf(from));
        memberFound();
        requestSaves();
        if ("NEW".equals(to)) {
            onlyRequestForMember();
        }

        terminationService.changeStatus(REQUEST_NO, to);

        assertThat(request.getStatus()).isEqualTo(TerminationRequestStatus.valueOf(to));
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({
            // Not in the table at all: a request on a board list is withdrawn by
            // deleting the list, and an approved one is Finance's to undo.
            "ADDED_TO_APPROVAL_LIST, NEW",
            "ADDED_TO_APPROVAL_LIST, INACTIVE",
            "APPROVED, NEW",
            "APPROVED, INACTIVE",
            // Statuses reached only by doing the work, never by a dropdown.
            "NEW, SUBMITTED_FOR_APPROVAL",
            "NEW, APPROVED",
            "INACTIVE, REJECTED",
            "REJECTED, APPROVED"
    })
    void refusesTransitionsOutsideTheSrsTable(String from, String to) {
        signInAs(Role.SUPER_ADMIN);
        requestIs(TerminationRequestStatus.valueOf(from));

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, to))
                .hasMessageContaining("cannot be changed from " + from + " to " + to);

        verify(requestRepository, never()).save(any(TerminationRequest.class));
    }

    // -------------------------------------------------- the member follows

    @Test
    void deactivatingARequestReturnsTheMemberToActive() {
        signInAs(Role.HEAD_OFFICE);
        requestIs(TerminationRequestStatus.NEW);
        memberFound();
        requestSaves();

        terminationService.changeStatus(REQUEST_NO, "INACTIVE");

        // SRS 2.2.4: "If the user changes the status of the Termination Request
        // to Inactive, the Member Profile Status will change back to Active."
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void revivingARequestPutsTheMemberBackIntoTerminationRequested() {
        signInAs(Role.HEAD_OFFICE);
        member.setStatus(MemberStatus.ACTIVE);
        requestIs(TerminationRequestStatus.INACTIVE);
        memberFound();
        requestSaves();
        onlyRequestForMember();

        terminationService.changeStatus(REQUEST_NO, "NEW");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.TERMINATION_REQUESTED);
    }

    @Test
    void movingBackToNewClearsTheReasonThatNoLongerApplies() {
        signInAs(Role.HEAD_OFFICE);
        request.setIncompleteReason("Passbook copy unclear");
        request.setRejectReason("Loan not settled");
        requestIs(TerminationRequestStatus.INCOMPLETE);
        memberFound();
        requestSaves();
        onlyRequestForMember();

        terminationService.changeStatus(REQUEST_NO, "NEW");

        assertThat(request.getIncompleteReason()).isNull();
        assertThat(request.getRejectReason()).isNull();
    }

    // ------------------------------------------------------ Inactive rights

    @Test
    void districtOfficeCannotDeactivateARequest() {
        signInAs(Role.DISTRICT_OFFICE);
        requestIs(TerminationRequestStatus.NEW);

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, "INACTIVE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not have rights");

        verify(requestRepository, never()).save(any(TerminationRequest.class));
    }

    @Test
    void districtOfficeCannotReviveAnInactiveRequestEither() {
        signInAs(Role.DISTRICT_OFFICE);
        requestIs(TerminationRequestStatus.INACTIVE);

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, "NEW"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("do not have rights");
    }

    @Test
    void districtOfficeMayStillMakeTransitionsThatDoNotTouchInactive() {
        signInAs(Role.DISTRICT_OFFICE);
        requestIs(TerminationRequestStatus.INCOMPLETE);
        memberFound();
        requestSaves();
        onlyRequestForMember();

        terminationService.changeStatus(REQUEST_NO, "NEW");

        assertThat(request.getStatus()).isEqualTo(TerminationRequestStatus.NEW);
    }

    @Test
    void anUnauthenticatedCallerHasNoInactiveRights() {
        requestIs(TerminationRequestStatus.NEW);

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, "INACTIVE"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ------------------------------------------------- one request in flight

    @Test
    void refusesToReviveARequestWhileAnotherIsAlreadyInFlight() {
        signInAs(Role.HEAD_OFFICE);
        requestIs(TerminationRequestStatus.INACTIVE);
        memberFound();

        TerminationRequest live = new TerminationRequest();
        live.setRequestNo("T-2026-002");
        live.setMemberId(MEMBER_ID);
        live.setStatus(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(request, live));

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, "NEW"))
                .hasMessageContaining("already has a termination request in progress");

        verify(requestRepository, never()).save(any(TerminationRequest.class));
    }

    @Test
    void aClosedRequestElsewhereDoesNotBlockRevival() {
        signInAs(Role.HEAD_OFFICE);
        requestIs(TerminationRequestStatus.INACTIVE);
        memberFound();
        requestSaves();

        TerminationRequest closed = new TerminationRequest();
        closed.setRequestNo("T-2026-002");
        closed.setMemberId(MEMBER_ID);
        closed.setStatus(TerminationRequestStatus.REJECTED);
        when(requestRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(request, closed));

        terminationService.changeStatus(REQUEST_NO, "NEW");

        assertThat(request.getStatus()).isEqualTo(TerminationRequestStatus.NEW);
    }

    // ------------------------------------------------------------ bad input

    @Test
    void rejectsAnUnknownStatusName() {
        signInAs(Role.SUPER_ADMIN);
        requestIs(TerminationRequestStatus.NEW);

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, "BANANA"))
                .hasMessageContaining("Unknown termination request status: BANANA");
    }

    @Test
    void rejectsAMissingStatus() {
        signInAs(Role.SUPER_ADMIN);
        requestIs(TerminationRequestStatus.NEW);

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, null))
                .hasMessageContaining("A target status is required");
    }

    @Test
    void rejectsAChangeToTheStatusItAlreadyHas() {
        signInAs(Role.SUPER_ADMIN);
        requestIs(TerminationRequestStatus.NEW);

        assertThatThrownBy(() -> terminationService.changeStatus(REQUEST_NO, "NEW"))
                .hasMessageContaining("is already NEW");
    }
}
