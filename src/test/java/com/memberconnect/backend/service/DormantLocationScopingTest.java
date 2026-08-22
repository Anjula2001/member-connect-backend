package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.DormantMemberDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.DormantApprovalListRepository;
import com.memberconnect.backend.repository.DormantConfigRepository;
import com.memberconnect.backend.repository.LoanObligationRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * Location scoping and role guards for the MMD12 dormant members list.
 *
 * Two rules are under test. First, the Location filter is a convenience for Head
 * Office and a boundary for the District Office - a district user sees their own
 * district whatever they ask for. Second, the District Office read-only view
 * that SRS 4.2.3 admits stops at reading: the board operations refuse it.
 *
 * Both matter more here than in the neighbouring modules, because before this
 * module was locked down every dormant endpoint answered to any authenticated
 * user, and the screen has always taken the requested locations at face value.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DormantLocationScopingTest {

    @Mock private MemberRepository memberRepository;
    @Mock private DormantConfigRepository configRepository;
    @Mock private DormantApprovalListRepository approvalListRepository;
    @Mock private BoardmeetingRepository boardMeetingRepository;
    @Mock private LoanObligationRepository obligationRepository;

    @Mock private MemberStatusHistoryService memberStatusHistoryService;

    @InjectMocks private DormantMembershipService dormantService;

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

    /**
     * submissionLocation is the District Office that administers the member;
     * educationalDistrict is where they work. The two are deliberately different
     * on every fixture here, so a test cannot pass by scoping on the wrong one.
     */
    private Member member(String memberId, String submissionLocation, String educationalDistrict) {
        Member member = new Member();
        member.setMemberId(memberId);
        member.setSubmissionLocation(submissionLocation);
        member.setEducationalDistrict(educationalDistrict);
        member.setStatus(MemberStatus.SELECTED_FOR_DORMANT);
        member.setLastActivityDate(LocalDate.of(2024, 1, 1));
        return member;
    }

    private List<DormantMemberDTO> search(List<String> requestedLocations) {
        return dormantService.searchDormantMembers(
                requestedLocations, null, "all", null, null, null, null, "memberId", "asc");
    }

    private void givenMembers(Member... members) {
        when(memberRepository.findByStatusIn(anyList())).thenReturn(List.of(members));
        when(memberRepository.findAll()).thenReturn(List.of(members));
        when(obligationRepository.existsByMemberId(anyString())).thenReturn(false);
    }

    // ---------------------------------------------------------------- scoping

    @Test
    void districtOfficeSeesOnlyItsOwnDistrictWhateverItAsksFor() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.DISTRICT_OFFICE, "Gampaha");

        // Asking for another district explicitly is the case that matters: the
        // dropdown never offers it, so anyone sending it is bypassing the UI.
        assertThat(search(List.of("Colombo")))
                .extracting(DormantMemberDTO::getMemberId)
                .containsExactly("M-1");
    }

    @Test
    void districtOfficeAskingForAllStillSeesOnlyItsOwnDistrict() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.DISTRICT_OFFICE, "Gampaha");

        assertThat(search(List.of("All")))
                .extracting(DormantMemberDTO::getMemberId)
                .containsExactly("M-1");
    }

    /**
     * Scoping keys on submissionLocation, not educationalDistrict. Every fixture
     * has them crossed over, so scoping on the working district would return
     * exactly the opposite member and this test would fail.
     */
    @Test
    void scopingUsesTheAdministeringOfficeNotTheWorkingDistrict() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.DISTRICT_OFFICE, "Colombo");

        assertThat(search(null))
                .extracting(DormantMemberDTO::getMemberId)
                .containsExactly("M-2");
    }

    @Test
    void aDistrictUserWithNoDistrictAssignedSeesNothingRatherThanEverything() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.DISTRICT_OFFICE, null);

        assertThat(search(List.of("All"))).isEmpty();
    }

    @Test
    void headOfficeHonoursTheRequestedLocations() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.HEAD_OFFICE, null);

        assertThat(search(List.of("Colombo")))
                .extracting(DormantMemberDTO::getMemberId)
                .containsExactly("M-2");
    }

    @Test
    void headOfficeAskingForAllSeesEveryDistrict() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.HEAD_OFFICE, null);

        assertThat(search(List.of("All")))
                .extracting(DormantMemberDTO::getMemberId)
                .containsExactlyInAnyOrder("M-1", "M-2");
    }

    /**
     * The filter dropdown is scoped the same way the rows are. Returning the
     * full district list here would leak every office's name to a district user
     * even though the results below it are correctly filtered.
     */
    @Test
    void theLocationDropdownIsScopedTheSameWayTheResultsAre() {
        givenMembers(
                member("M-1", "Gampaha", "Colombo"),
                member("M-2", "Colombo", "Gampaha"));
        signInAs(Role.DISTRICT_OFFICE, "Gampaha");

        assertThat(dormantService.getLocations()).containsExactly("Gampaha");
    }

    // ----------------------------------------------------------- role guards

    @Test
    void districtOfficeMayReadTheDormantList() {
        givenMembers(member("M-1", "Gampaha", "Colombo"));
        signInAs(Role.DISTRICT_OFFICE, "Gampaha");

        assertThat(search(null)).hasSize(1);
    }

    @Test
    void districtOfficeMayNotTouchTheBoardHalf() {
        signInAs(Role.DISTRICT_OFFICE, "Gampaha");

        assertThatThrownBy(() -> dormantService.getAllApprovalLists())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void districtOfficeMayNotRunTheIdentificationProcess() {
        signInAs(Role.DISTRICT_OFFICE, "Gampaha");

        assertThatThrownBy(() -> dormantService.runIdentification())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    /**
     * The roles this module has no business with at all. Before the lockdown
     * each of these could run identification and inactivate members.
     */
    @Test
    void rolesWithNoPartInTheDormantFlowAreRefusedOutright() {
        for (Role role : List.of(Role.ACCOUNTS, Role.SCHOLARSHIP_OFFICER,
                Role.DEATH_DONATION_OFFICER, Role.DISTRICT_COMMITTEE, Role.PD_COMMITTEE)) {
            signInAs(role, null);

            assertThatThrownBy(() -> search(null))
                    .as("%s must not be able to read the dormant list", role)
                    .isInstanceOf(ResponseStatusException.class);

            assertThatThrownBy(() -> dormantService.runIdentification())
                    .as("%s must not be able to run identification", role)
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    /**
     * MMD15 deletion is a privilege separate from ordinary Head Office access -
     * the one asymmetry in this matrix that narrows rather than widens, and so
     * the one most likely to be "corrected" by a later reader.
     */
    @Test
    void headOfficeMayManageListsButNotDeleteThem() {
        signInAs(Role.HEAD_OFFICE, null);

        assertThatThrownBy(() -> dormantService.deleteApprovalList("DAL-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void boardSecretaryMayDeleteLists() {
        signInAs(Role.BOARD_SECRETARY, null);

        // Gets past the guard and fails on the missing list instead, which is
        // what "allowed through" looks like without stubbing a whole fixture.
        assertThatThrownBy(() -> dormantService.deleteApprovalList("DAL-missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    /**
     * The scheduler runs MMD10 with no authentication at all. Refusing a null
     * principal would mean the monthly run could never fire.
     */
    @Test
    void theUnauthenticatedSchedulerMayStillRunIdentification() {
        SecurityContextHolder.clearContext();
        when(memberRepository.findByStatusIn(anyList())).thenReturn(List.of());
        when(memberRepository.findByStatus(MemberStatus.ACTIVE)).thenReturn(List.of());
        when(configRepository.findAll()).thenReturn(List.of(new com.memberconnect.backend.model.DormantConfig()));

        assertThat(dormantService.runIdentification()).containsExactly(0, 0);
    }
}
