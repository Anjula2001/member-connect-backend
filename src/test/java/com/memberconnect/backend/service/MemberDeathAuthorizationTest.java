package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
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

import com.memberconnect.backend.enums.MemberDeathRecordStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.CauseOfDeathRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.LoanRepository;
import com.memberconnect.backend.repository.MemberDeathDocumentRepository;
import com.memberconnect.backend.repository.MemberDeathMinorAccountRepository;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;

/**
 * Role authorization for Record Member Death (SRS section 4).
 *
 * The controller annotations are the outer gate, but the generic document routes
 * in DocumentController carry no annotations of their own - they call into this
 * service for authority instead. These tests cover that inner gate, plus the
 * per-level decision rule that stops one clerk walking a record through all three
 * approval levels.
 *
 * Pure Mockito: no Spring context and no database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberDeathAuthorizationTest {

    @Mock private MemberDeathRecordRepository recordRepository;
    @Mock private MemberDeathDocumentRepository documentRepository;
    @Mock private MemberDeathMinorAccountRepository minorAccountRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CauseOfDeathRepository causeOfDeathRepository;
    @Mock private MinorSavingsAccountRepository minorSavingsAccountRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanObligationRepository obligationRepository;
    @Mock private BankRepository bankRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private S3Service s3Service;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;
    @Mock private DeathDonationEntitlementService entitlementService;
    @Mock private DocumentService documentService;

    @Mock private MemberStatusHistoryService memberStatusHistoryService;

    @InjectMocks private MemberDeathRecordService service;

    private static final String RECORD_NO = "MDR0001";
    private static final String DISTRICT = "Colombo";
    private static final String AUTHOR = "district-clerk";

    /** Roles the SRS names as actors anywhere in section 4. */
    private static final Role[] WORKFLOW_ROLES = {
        Role.DISTRICT_OFFICE, Role.DISTRICT_COMMITTEE, Role.PD_COMMITTEE,
        Role.HEAD_OFFICE, Role.BOARD_SECRETARY, Role.SUPER_ADMIN
    };

    /** Roles that exist in the system but are not actors in section 4. */
    private static final Role[] OUTSIDE_ROLES = {
        Role.ACCOUNTS, Role.SCHOLARSHIP_OFFICER, Role.DEATH_DONATION_OFFICER
    };

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // MMT18: uploading and deleting supporting documents
    // ------------------------------------------------------------------

    @Test
    void districtOfficeMayChangeDocumentsOnAnUnsubmittedRecord() {
        givenRecord(MemberDeathRecordStatus.NEW);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThatCode(() -> service.assertDocumentsEditable(RECORD_NO))
                .doesNotThrowAnyException();
    }

    /**
     * The roles that decide a record are not the roles that assemble it. Before
     * this check existed, any authenticated user reached the generic upload and
     * delete routes, because those routes carry no annotations.
     */
    @Test
    void nonEntryRolesMayNotChangeDocuments() {
        givenRecord(MemberDeathRecordStatus.NEW);

        for (Role role : new Role[] {
                Role.DISTRICT_COMMITTEE, Role.PD_COMMITTEE, Role.HEAD_OFFICE, Role.BOARD_SECRETARY }) {
            signIn(role, null, role.name());

            assertThat(statusOf(catchThrowable(() -> service.assertDocumentsEditable(RECORD_NO))))
                    .as("%s must not upload or delete death documents", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void rolesOutsideTheDeathWorkflowMayNotChangeDocuments() {
        givenRecord(MemberDeathRecordStatus.NEW);

        for (Role role : OUTSIDE_ROLES) {
            signIn(role, null, role.name());

            assertThat(statusOf(catchThrowable(() -> service.assertDocumentsEditable(RECORD_NO))))
                    .as("%s must not upload or delete death documents", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /** District scoping still applies on top of the role check. */
    @Test
    void districtOfficeMayNotChangeAnotherDistrictsDocuments() {
        givenRecord(MemberDeathRecordStatus.NEW);
        signIn(Role.DISTRICT_OFFICE, "Kandy", "kandy-clerk");

        assertThat(statusOf(catchThrowable(() -> service.assertDocumentsEditable(RECORD_NO))))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** MMT18: the Add and Delete buttons are gone once the record is submitted. */
    @Test
    void documentsLockOnceTheRecordIsSubmitted() {
        givenRecord(MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, "another-clerk");

        assertThat(statusOf(catchThrowable(() -> service.assertDocumentsEditable(RECORD_NO))))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anUnauthenticatedCallerMayNotChangeDocuments() {
        givenRecord(MemberDeathRecordStatus.NEW);

        assertThat(statusOf(catchThrowable(() -> service.assertDocumentsEditable(RECORD_NO))))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // MMT20 / MMT22-MMT24: reading supporting documents
    // ------------------------------------------------------------------

    @Test
    void everyDeathWorkflowRoleMayReadDocuments() {
        givenRecord(MemberDeathRecordStatus.DISTRICT_COMMITTEE);

        for (Role role : WORKFLOW_ROLES) {
            // Only the District Office is district-scoped; the rest are signed in
            // without an assigned district.
            signIn(role, role == Role.DISTRICT_OFFICE ? DISTRICT : null, role.name());

            assertThatCode(() -> service.assertDocumentsReadable(RECORD_NO))
                    .as("%s should be able to read a death record's documents", role)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rolesOutsideTheDeathWorkflowMayNotReadDocuments() {
        givenRecord(MemberDeathRecordStatus.DISTRICT_COMMITTEE);

        for (Role role : OUTSIDE_ROLES) {
            signIn(role, null, role.name());

            assertThat(statusOf(catchThrowable(() -> service.assertDocumentsReadable(RECORD_NO))))
                    .as("%s must not read death documents", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /** The preview route has no record to scope against, so role alone decides. */
    @Test
    void previewRouteChecksRoleOnly() {
        signIn(Role.DEATH_DONATION_OFFICER, null, "dd-officer");
        assertThat(statusOf(catchThrowable(() -> service.assertMayReadDeathRecords())))
                .isEqualTo(HttpStatus.FORBIDDEN);

        signIn(Role.DISTRICT_OFFICE, DISTRICT, "clerk");
        assertThatCode(() -> service.assertMayReadDeathRecords()).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // MMT22 / MMT23 / MMT24: the three decision levels
    // ------------------------------------------------------------------

    /**
     * A record waiting on the District Office cannot be approved by the District
     * Committee, and so on up the ladder. This is what keeps the escalation honest.
     */
    @Test
    void aDecisionBelongsToTheRoleThatOwnsTheCurrentLevel() {
        assertCannotApprove(MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL, Role.DISTRICT_COMMITTEE);
        assertCannotApprove(MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL, Role.PD_COMMITTEE);
        assertCannotApprove(MemberDeathRecordStatus.DISTRICT_COMMITTEE, Role.DISTRICT_OFFICE);
        assertCannotApprove(MemberDeathRecordStatus.DISTRICT_COMMITTEE, Role.PD_COMMITTEE);
        assertCannotApprove(MemberDeathRecordStatus.PD_COMMITTEE, Role.DISTRICT_OFFICE);
        assertCannotApprove(MemberDeathRecordStatus.PD_COMMITTEE, Role.DISTRICT_COMMITTEE);
    }

    /** A record that is not sitting at a decision level is not decidable at all. */
    @Test
    void anAlreadyApprovedRecordCannotBeApprovedAgain() {
        givenRecord(MemberDeathRecordStatus.APPROVED);
        signIn(Role.PD_COMMITTEE, null, "pd-user");

        assertThat(statusOf(catchThrowable(() -> service.approveRecord(RECORD_NO))))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * MMT18 raises, MMT22 decides. Both map to DISTRICT_OFFICE here, so the SRS's
     * separation of the clerk from the "Authorized User" is enforced by refusing to
     * let the author decide their own record.
     */
    @Test
    void theAuthorOfARecordCannotApproveIt() {
        givenRecord(MemberDeathRecordStatus.SUBMITTED_FOR_APPROVAL);
        signIn(Role.DISTRICT_OFFICE, DISTRICT, AUTHOR);

        assertThat(statusOf(catchThrowable(() -> service.approveRecord(RECORD_NO))))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void assertCannotApprove(MemberDeathRecordStatus level, Role wrongRole) {
        givenRecord(level);
        signIn(wrongRole, null, wrongRole.name());

        assertThat(statusOf(catchThrowable(() -> service.approveRecord(RECORD_NO))))
                .as("%s must not approve a record sitting at %s", wrongRole, level)
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void signIn(Role role, String district, String username) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        user.setAssignedDistrict(district);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private MemberDeathRecord givenRecord(MemberDeathRecordStatus status) {
        Member member = new Member();
        member.setMemberId("M1001");
        member.setSubmissionLocation(DISTRICT);

        MemberDeathRecord record = new MemberDeathRecord();
        record.setRecordId(RECORD_NO);
        record.setStatus(status);
        record.setMember(member);
        record.setSubmissionLocation(DISTRICT);
        record.setCreatedBy(AUTHOR);

        when(recordRepository.findByRecordId(RECORD_NO)).thenReturn(Optional.of(record));
        return record;
    }

    /** Reads the HTTP status off a thrown ResponseStatusException, failing clearly otherwise. */
    private static HttpStatus statusOf(Throwable thrown) {
        assertThat(thrown)
                .as("expected the call to be refused")
                .isInstanceOf(ResponseStatusException.class);
        return HttpStatus.valueOf(((ResponseStatusException) thrown).getStatusCode().value());
    }
}
