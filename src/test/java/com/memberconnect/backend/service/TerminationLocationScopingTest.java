package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.memberconnect.backend.dto.TerminationRequestResponseDTO;
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
 * Location scoping for the MMT02 requests list (SRS section 2.2.2).
 *
 * The rule being tested is that the Location filter is a convenience for Head
 * Office and a boundary for the District Office: a district user sees their own
 * district whatever they ask for.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminationLocationScopingTest {

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


    @Mock private MemberStatusHistoryService memberStatusHistoryService;

    @InjectMocks private TerminationService terminationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(Role role, String assignedDistrict) {
        User user = new User();
        user.setUsername("tester");
        user.setRole(role);
        user.setAssignedDistrict(assignedDistrict);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    private long nextId = 1L;

    private TerminationRequest request(String requestNo, String memberId, String location) {
        TerminationRequest request = new TerminationRequest();
        // The response mapper keys minor disbursements by request id, and an
        // immutable map rejects a null key, so ids must be real here. The entity
        // has no setter because the id is database-generated.
        ReflectionTestUtils.setField(request, "id", nextId++);
        request.setRequestNo(requestNo);
        request.setMemberId(memberId);
        request.setSubmissionLocation(location);
        request.setStatus(TerminationRequestStatus.NEW);
        request.setRequestedDate(LocalDate.of(2026, 8, 1));
        return request;
    }

    private void givenRequests(TerminationRequest... requests) {
        when(requestRepository.findAll()).thenReturn(List.of(requests));

        List<Member> members = List.of(requests).stream().map(r -> {
            Member member = new Member();
            member.setMemberId(r.getMemberId());
            return member;
        }).toList();

        when(memberRepository.findByMemberIdIn(anyList())).thenReturn(members);
    }

    private List<String> requestNosFrom(List<TerminationRequestResponseDTO> results) {
        return results.stream().map(TerminationRequestResponseDTO::getRequestNo).toList();
    }

    private List<TerminationRequestResponseDTO> search(List<String> locations) {
        return terminationService.searchRequests(
                null, null, null, null, "requestedDate", "asc", locations
        );
    }

    @Test
    void districtUserSeesOnlyTheirOwnDistrict() {
        signInAs(Role.DISTRICT_OFFICE, "Colombo");
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy")
        );

        assertThat(requestNosFrom(search(null))).containsExactly("T-1");
    }

    @Test
    void districtUserCannotWidenTheirScopeByAskingForAnotherDistrict() {
        signInAs(Role.DISTRICT_OFFICE, "Colombo");
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy")
        );

        // The whole point of scoping server-side: the request parameter is not
        // the boundary, the principal is.
        assertThat(requestNosFrom(search(List.of("Kandy")))).containsExactly("T-1");
        assertThat(requestNosFrom(search(List.of("Colombo", "Kandy")))).containsExactly("T-1");
    }

    @Test
    void districtUserWithNoAssignedDistrictSeesNothingRatherThanEverything() {
        signInAs(Role.DISTRICT_OFFICE, null);
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy")
        );

        assertThat(search(null)).isEmpty();
    }

    @Test
    void headOfficeSeesEveryLocationByDefault() {
        signInAs(Role.HEAD_OFFICE, null);
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy")
        );

        assertThat(requestNosFrom(search(null))).containsExactly("T-1", "T-2");
    }

    @Test
    void headOfficeCanNarrowToChosenLocations() {
        signInAs(Role.HEAD_OFFICE, null);
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy"),
                request("T-3", "M-3", "Galle")
        );

        assertThat(requestNosFrom(search(List.of("Kandy", "Galle"))))
                .containsExactly("T-2", "T-3");
    }

    @Test
    void allIsTreatedAsNoRestriction() {
        signInAs(Role.HEAD_OFFICE, null);
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy")
        );

        assertThat(requestNosFrom(search(List.of("All")))).containsExactly("T-1", "T-2");
    }

    @Test
    void rowsWithNoLocationStayVisibleToHeadOfficeButNotToADistrict() {
        givenRequests(
                request("T-1", "M-1", null),
                request("T-2", "M-2", "Colombo")
        );

        // Legacy rows predating the column: Head Office keeps sight of them so
        // they can be found and backfilled...
        signInAs(Role.HEAD_OFFICE, null);
        assertThat(requestNosFrom(search(null))).containsExactly("T-1", "T-2");

        // ...but a district user must not inherit them, because a null location
        // cannot be shown to belong to their district.
        SecurityContextHolder.clearContext();
        signInAs(Role.DISTRICT_OFFICE, "Colombo");
        assertThat(requestNosFrom(search(null))).containsExactly("T-2");
    }

    @Test
    void anUnauthenticatedCallerIsNotLocationScoped() {
        // Reachable only in direct service-level use; the endpoint itself is
        // behind @PreAuthorize.
        givenRequests(
                request("T-1", "M-1", "Colombo"),
                request("T-2", "M-2", "Kandy")
        );

        assertThat(requestNosFrom(search(null))).containsExactly("T-1", "T-2");
    }
}
