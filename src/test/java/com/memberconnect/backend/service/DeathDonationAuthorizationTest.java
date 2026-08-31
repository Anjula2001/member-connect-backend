package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.enums.DeathDonationRequestStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.DeathDonationRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.DeathDonationDocumentRepository;
import com.memberconnect.backend.repository.DeathDonationRelationshipRepository;
import com.memberconnect.backend.repository.DeathDonationRequestRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.RequiredDocumentRepository;
import com.memberconnect.backend.service.finance.FinanceDeathDonationClient;

/**
 * Role authorization for Death Donations for Members (SRS Requirement 05,
 * section 2).
 *
 * The controller annotations are the outer gate; these tests cover the inner
 * one - district isolation, per-level decision ownership and the self-approval
 * block - none of which an annotation can express, because none of them can be
 * decided without looking at the record.
 *
 * Pure Mockito: no Spring context and no database, so this runs without touching
 * the shared remote Postgres the integration tests use.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeathDonationAuthorizationTest {

    @Mock private DeathDonationRequestRepository requestRepository;
    @Mock private DeathDonationDocumentRepository documentRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RequiredDocumentRepository requiredDocumentRepository;
    @Mock private DeathDonationRelationshipRepository relationshipRepository;
    @Mock private S3Service s3Service;
    @Mock private DeathDonationEntitlementService entitlementService;
    @Mock private FinanceDeathDonationClient financeClient;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private DeathDonationService service;

    private static final String REQUEST_NO = "DD-2026-001";
    private static final String DISTRICT = "Colombo";
    private static final String OTHER_DISTRICT = "Kandy";
    private static final String AUTHOR = "district-clerk";

    /** Roles that exist in the system but are not actors in SRS section 2. */
    private static final Role[] OUTSIDE_ROLES = {
        Role.ACCOUNTS, Role.SCHOLARSHIP_OFFICER, Role.DEATH_DONATION_OFFICER
    };

    @BeforeEach
    void stubSaveAsIdentity() {
        when(requestRepository.save(any(DeathDonationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(relationshipRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Who may see a death donation request at all
    // ------------------------------------------------------------------

    @Test
    void rolesOutsideTheDonationFlowMayNotReadRequests() {
        givenRequest(DeathDonationRequestStatus.NEW);

        for (Role role : OUTSIDE_ROLES) {
            signIn(role, null, role.name());

            assertThat(statusOf(catchThrowable(() -> service.getRequestByRequestNo(REQUEST_NO))))
                    .as("%s is not an actor in SRS section 2 and must not read donations", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void headOfficeMayReadAnyDistrictRequest() {
        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.HEAD_OFFICE, null, "head-office");

        assertThatCode(() -> service.getRequestByRequestNo(REQUEST_NO))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // District isolation
    // ------------------------------------------------------------------

    @Test
    void aDistrictOfficeUserMayNotOpenAnotherDistrictRequest() {
        givenRequest(DeathDonationRequestStatus.NEW);
        signIn(Role.DISTRICT_OFFICE, OTHER_DISTRICT, "other-district-clerk");

        assertThat(statusOf(catchThrowable(() -> service.getRequestByRequestNo(REQUEST_NO))))
                .as("a district user must not reach another district's donation request")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aDistrictOfficeUserMayOpenTheirOwnDistrictRequest() {
        givenRequest(DeathDonationRequestStatus.NEW);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThatCode(() -> service.getRequestByRequestNo(REQUEST_NO))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Entry rights (MMD01 / MMD04)
    // ------------------------------------------------------------------

    @Test
    void onlyTheDistrictOfficeMaySubmit() {
        for (Role role : new Role[] {
                Role.DISTRICT_COMMITTEE, Role.PD_COMMITTEE, Role.HEAD_OFFICE, Role.BOARD_SECRETARY }) {
            givenRequest(DeathDonationRequestStatus.NEW);
            signIn(role, null, role.name());

            assertThat(statusOf(catchThrowable(() -> service.submitRequest(REQUEST_NO))))
                    .as("%s must not submit a donation request", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ------------------------------------------------------------------
    // Per-level decision ownership (MMD05 / MMD06 / MMD07)
    // ------------------------------------------------------------------

    @Test
    void theDistrictCommitteeMayNotDecideARequestStillAtTheDistrictOffice() {
        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_COMMITTEE, null, "committee-user");

        assertThat(statusOf(catchThrowable(() -> service.approveRequest(REQUEST_NO))))
                .as("level 2 must not reach past level 1")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theDistrictOfficeMayNotDecideARequestSittingWithTheCommittee() {
        givenRequest(DeathDonationRequestStatus.DISTRICT_COMMITTEE);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThat(statusOf(catchThrowable(() -> service.approveRequest(REQUEST_NO))))
                .as("a request escalated to the committee is no longer the office's to decide")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void eachLevelMayDecideItsOwnRequests() {
        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");
        assertThatCode(() -> service.approveRequest(REQUEST_NO)).doesNotThrowAnyException();

        givenRequest(DeathDonationRequestStatus.DISTRICT_COMMITTEE);
        signIn(Role.DISTRICT_COMMITTEE, null, "committee-user");
        assertThatCode(() -> service.approveRequest(REQUEST_NO)).doesNotThrowAnyException();

        givenRequest(DeathDonationRequestStatus.PD_COMMITTEE);
        signIn(Role.PD_COMMITTEE, null, "pd-user");
        assertThatCode(() -> service.approveRequest(REQUEST_NO)).doesNotThrowAnyException();
    }

    @Test
    void aRequestThatHasNotBeenSubmittedCannotBeDecided() {
        givenRequest(DeathDonationRequestStatus.NEW);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThat(statusOf(catchThrowable(() -> service.approveRequest(REQUEST_NO))))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Segregation of duty
    // ------------------------------------------------------------------

    /**
     * The SRS separates the clerk who raises a request (MMD01) from the
     * "Authorized User" who decides it (MMD05). Both are DISTRICT_OFFICE here,
     * so this refusal is what keeps them apart.
     */
    @Test
    void theClerkWhoRaisedARequestMayNotDecideIt() {
        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, AUTHOR);

        assertThat(statusOf(catchThrowable(() -> service.approveRequest(REQUEST_NO))))
                .as("self-approval must be refused")
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(statusOf(catchThrowable(() -> service.rejectRequest(REQUEST_NO, "no"))))
                .as("self-rejection must be refused too")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void superAdminBypassesTheSelfApprovalBlock() {
        DeathDonationRequest request = givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        request.setCreatedBy("super");
        signIn(Role.SUPER_ADMIN, null, "super");

        assertThatCode(() -> service.approveRequest(REQUEST_NO)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // MMD04 status matrix and the Inactive right
    // ------------------------------------------------------------------

    @Test
    void makingARequestInactiveNeedsInactiveRights() {
        givenRequest(DeathDonationRequestStatus.NEW);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThat(statusOf(catchThrowable(() -> service.changeStatus(REQUEST_NO, "INACTIVE"))))
                .as("the District Office has no inactive rights")
                .isEqualTo(HttpStatus.FORBIDDEN);

        givenRequest(DeathDonationRequestStatus.NEW);
        signIn(Role.HEAD_OFFICE, null, "head-office");

        assertThatCode(() -> service.changeStatus(REQUEST_NO, "INACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void theStatusMatrixRefusesTransitionsTheSrsDoesNotList() {
        givenRequest(DeathDonationRequestStatus.NEW);
        signIn(Role.HEAD_OFFICE, null, "head-office");

        // SRS p.24 allows New -> Inactive only; New -> Approved is not on the table.
        assertThat(statusOf(catchThrowable(() -> service.changeStatus(REQUEST_NO, "APPROVED"))))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * APPROVED has no row in the SRS matrix, and by then the request has gone to
     * the Finance Module, so walking it back would desynchronise the two systems.
     */
    @Test
    void anApprovedRequestCannotBeWalkedBack() {
        givenRequest(DeathDonationRequestStatus.APPROVED);
        signIn(Role.HEAD_OFFICE, null, "head-office");

        assertThat(statusOf(catchThrowable(() -> service.changeStatus(REQUEST_NO, "NEW"))))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Escalation (MMD05 -> MMD06 -> MMD07)
    // ------------------------------------------------------------------

    @Test
    void escalationWalksTheLadderOneLevelAtATime() {
        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");
        assertThat(service.forwardToNextLevel(REQUEST_NO, "needs review").getStatus())
                .isEqualTo("DISTRICT_COMMITTEE");

        givenRequest(DeathDonationRequestStatus.DISTRICT_COMMITTEE);
        signIn(Role.DISTRICT_COMMITTEE, null, "committee-user");
        assertThat(service.forwardToNextLevel(REQUEST_NO, null).getStatus())
                .isEqualTo("PD_COMMITTEE");
    }

    @Test
    void theTopLevelHasNowhereToEscalateTo() {
        givenRequest(DeathDonationRequestStatus.PD_COMMITTEE);
        signIn(Role.PD_COMMITTEE, null, "pd-user");

        assertThat(statusOf(catchThrowable(() -> service.forwardToNextLevel(REQUEST_NO, null))))
                .as("the P&D Committee is the last level; it decides rather than forwards")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Entitlement amounts (SRS 2.2.3)
    // ------------------------------------------------------------------

    @Test
    void onlyDecisionRolesMayChangeTheDonationAmounts() {
        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.BOARD_SECRETARY, null, "board-secretary");

        assertThat(statusOf(catchThrowable(
                () -> service.refreshDonationEntitlement(REQUEST_NO, 24, null, null))))
                .as("oversight roles read the figures but do not set them")
                .isEqualTo(HttpStatus.FORBIDDEN);

        givenRequest(DeathDonationRequestStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThatCode(() -> service.refreshDonationEntitlement(REQUEST_NO, 24, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void theEntitlementFreezesOnceTheRequestIsDecided() {
        givenRequest(DeathDonationRequestStatus.APPROVED);
        signIn(Role.PD_COMMITTEE, null, "pd-user");

        assertThat(statusOf(catchThrowable(
                () -> service.refreshDonationEntitlement(REQUEST_NO, 60, null, null))))
                .as("an approved donation has already been handed to Finance")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void signIn(Role role, String district, String username) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        user.setAssignedDistrict(district);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private DeathDonationRequest givenRequest(DeathDonationRequestStatus status) {
        Member member = new Member();
        member.setMemberId("M1001");
        member.setSubmissionLocation(DISTRICT);

        DeathDonationRequest request = new DeathDonationRequest();
        request.setRequestNo(REQUEST_NO);
        request.setStatus(status);
        request.setMember(member);
        request.setSubmissionLocation(DISTRICT);
        request.setCreatedBy(AUTHOR);
        request.setRelationshipToDeceased("Father");
        request.setRequestedDate(java.time.LocalDate.now());
        request.setDeceasedName("A. Perera");
        request.setDeceasedDate(java.time.LocalDate.now().minusDays(10));
        request.setDeathCertificateNumber("DC-1");

        when(requestRepository.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(request));
        return request;
    }

    /** Reads the HTTP status off a thrown ResponseStatusException, failing clearly otherwise. */
    private static HttpStatus statusOf(Throwable thrown) {
        assertThat(thrown)
                .as("expected the call to be refused")
                .isInstanceOf(ResponseStatusException.class);
        return HttpStatus.valueOf(((ResponseStatusException) thrown).getStatusCode().value());
    }
}
