package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memberconnect.backend.dto.Grade5StudentDTO;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Grade5ExamMaster;
import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.Grade5ExamMasterRepository;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;
import com.memberconnect.backend.repository.ScholarshipConfigRepository;
import com.memberconnect.backend.repository.ScholarshipRemittanceRepository;
import com.memberconnect.backend.config.CurrentUserService;

@ExtendWith(MockitoExtension.class)
class Grade5ScholarshipServiceTest {

    @InjectMocks
    private Grade5ScholarshipService service;

    @Mock
    private Grade5ScholarshipRepository repository;

    @Mock
    private MinorSavingsAccountRepository minorRepo;

    @Mock
    private Grade5ExamMasterRepository examMasterRepository;

    @Mock
    private MemberRepository memberRepository;

    // Collaborators the service reads during saveRequest but that no test stubs.
    // Left unmocked they are simply null and every save-path test dies on an NPE
    // before reaching its assertion. Mockito's defaults are enough here: the config
    // lookups fall back to Optional.empty() (so the service uses its @Value defaults)
    // and there is no authenticated user, which is correct for a unit test.
    @Mock
    private ScholarshipConfigRepository scholarshipConfigRepository;

    @Mock
    private ScholarshipRemittanceRepository remittanceRepository;

    @Mock
    private CurrentUserService currentUserService;

    // Unstubbed, statusOn returns null - "history knows nothing about that date" -
    // which is exactly the state of a member whose status changed before this table
    // existed, and the case the fallback to Member.status is there for.
    @Mock
    private MemberStatusHistoryService memberStatusHistoryService;

    @Test
    void saveRequestShouldRejectWhenMemberWasNotActiveDuringSelectedExam() {
        Member member = new Member();
        member.setMemberId("M-001");
        member.setStatus(MemberStatus.INACTIVE);
        member.setMembershipStartDate(LocalDate.of(2025, 1, 1));

        Grade5ExamMaster examMaster = new Grade5ExamMaster();
        examMaster.setYear(2024);
        examMaster.setExamDate(LocalDate.of(2024, 12, 31));

        Grade5StudentDTO dto = new Grade5StudentDTO();
        dto.setRequestedDate("2025-01-02");
        dto.setStudentName("Test Student");
        dto.setBirthCertificateNumber("BC123456");
        dto.setStudentSchool("Test School");
        dto.setSchoolDistrict("Colombo");
        dto.setExamYear(2024);
        dto.setExaminationNumber("EXAM123456");
        dto.setDistrictCutOffMark(100);
        dto.setMarksObtained(90);

        when(memberRepository.findByMemberId("M-001")).thenReturn(Optional.of(member));
        when(examMasterRepository.findById(2024)).thenReturn(Optional.of(examMaster));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.saveRequest("M-001", dto)
        );

        assertEquals(
                "The Grade 5 Scholarship Request cannot be saved. The Member is not Active during the selected Exam",
                exception.getMessage()
        );
    }

    @Test
    void saveRequestShouldFlagDeviationWhenRequestFallsOutsideEligibilityWindow() {
        Member member = new Member();
        member.setMemberId("M-002");
        member.setStatus(MemberStatus.ACTIVE);
        member.setMembershipStartDate(LocalDate.of(2020, 1, 1));

        Grade5ExamMaster examMaster = new Grade5ExamMaster();
        examMaster.setYear(2024);
        examMaster.setExamDate(LocalDate.of(2024, 12, 31));

        Grade5StudentDTO dto = new Grade5StudentDTO();
        dto.setRequestedDate("2026-01-02");
        dto.setStudentName("Test Student");
        dto.setBirthCertificateNumber("BC123456");
        dto.setStudentSchool("Test School");
        dto.setSchoolDistrict("Colombo");
        dto.setExamYear(2024);
        dto.setExaminationNumber("EXAM654321");
        dto.setDistrictCutOffMark(100);
        dto.setMarksObtained(90);
        // No minor account, so the whole scholarship is paid to the member. Without
        // this the save stops at the fund disbursement gate and never reaches the
        // deviation check this test is about.
        dto.setMinorAccountExists(false);
        dto.setDisbursementOption("MEMBER_ONLY");

        AtomicReference<com.memberconnect.backend.model.Grade5ScholarshipRequest> savedRequest = new AtomicReference<>();

        when(memberRepository.findByMemberId("M-002")).thenReturn(Optional.of(member));
        when(examMasterRepository.findById(2024)).thenReturn(Optional.of(examMaster));
        when(repository.existsByExaminationNumber("EXAM654321")).thenReturn(false);
        // Scholarship account remitted for every month checked, so the save gets past
        // the remittance gate and the assertion below is actually about deviation.
        when(remittanceRepository.existsByMember_IdAndRemittanceMonthAndRemittedTrue(any(), any()))
                .thenReturn(true);
        when(repository.save(any(com.memberconnect.backend.model.Grade5ScholarshipRequest.class))).thenAnswer(invocation -> {
            com.memberconnect.backend.model.Grade5ScholarshipRequest request = invocation.getArgument(0);
            savedRequest.set(request);
            return request;
        });

        com.memberconnect.backend.model.Grade5ScholarshipRequest result = service.saveRequest("M-002", dto);

        assertTrue(result.getHasDeviation());
        assertTrue(savedRequest.get().getHasDeviation());
    }

    @Test
    void submitRequestShouldNotRevalidateMemberActivity() {
        Grade5ScholarshipRequest request = new Grade5ScholarshipRequest();
        request.setRequestNo("G5-2026-001");
        request.setMemberId("M-003");
        request.setExamYear(2024);
        request.setStatus("NEW");
        request.setHasDeviation(false);

        when(repository.findByRequestNo("G5-2026-001")).thenReturn(Optional.of(request));
        when(repository.save(any(Grade5ScholarshipRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Grade5ScholarshipRequest result = service.submitRequest("G5-2026-001", "SUBMITTED_FOR_NORMAL_APPROVAL");

        assertEquals("SUBMITTED_FOR_NORMAL_APPROVAL", result.getStatus());
    }

    @Test
    void saveRequestShouldRejectWhenMembershipPeriodLessThan36Months() {
        Member member = new Member();
        member.setMemberId("M-001");
        member.setStatus(MemberStatus.ACTIVE);
        member.setMembershipStartDate(LocalDate.of(2024, 1, 1));

        Grade5ExamMaster examMaster = new Grade5ExamMaster();
        examMaster.setYear(2024);
        examMaster.setExamDate(LocalDate.of(2024, 12, 31));

        Grade5StudentDTO dto = new Grade5StudentDTO();
        dto.setRequestedDate("2025-01-02");
        dto.setStudentName("Test Student");
        dto.setBirthCertificateNumber("BC123456");
        dto.setStudentSchool("Test School");
        dto.setSchoolDistrict("Colombo");
        dto.setExamYear(2024);
        dto.setExaminationNumber("EXAM123456");
        dto.setDistrictCutOffMark(100);
        dto.setMarksObtained(120);

        when(memberRepository.findByMemberId("M-001")).thenReturn(Optional.of(member));
        when(examMasterRepository.findById(2024)).thenReturn(Optional.of(examMaster));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.saveRequest("M-001", dto)
        );

        assertEquals(
                "The required continues Membership period does not comply (36 months)",
                exception.getMessage()
        );
    }

    private static final String NOT_ACTIVE_AT_EXAM =
            "The Grade 5 Scholarship Request cannot be saved. The Member is not Active during the selected Exam";

    private Grade5StudentDTO examDto() {
        Grade5StudentDTO dto = new Grade5StudentDTO();
        dto.setRequestedDate("2025-01-02");
        dto.setStudentName("Test Student");
        dto.setBirthCertificateNumber("BC123456");
        dto.setStudentSchool("Test School");
        dto.setSchoolDistrict("Colombo");
        dto.setExamYear(2024);
        dto.setExaminationNumber("EXAM123456");
        dto.setDistrictCutOffMark(100);
        dto.setMarksObtained(120);
        return dto;
    }

    private Grade5ExamMaster exam2024() {
        Grade5ExamMaster examMaster = new Grade5ExamMaster();
        examMaster.setYear(2024);
        examMaster.setExamDate(LocalDate.of(2024, 12, 31));
        return examMaster;
    }

    /**
     * The member is Active now, so Member.status alone would let this through. The
     * history says they were Inactive on exam day, and that is the fact MMS02 asks
     * about.
     */
    @Test
    void saveRequestShouldRejectWhenHistorySaysInactiveOnExamDateEvenIfActiveNow() {
        Member member = new Member();
        member.setMemberId("M-003");
        member.setStatus(MemberStatus.ACTIVE);
        member.setMembershipStartDate(LocalDate.of(2018, 1, 1));

        when(memberRepository.findByMemberId("M-003")).thenReturn(Optional.of(member));
        when(examMasterRepository.findById(2024)).thenReturn(Optional.of(exam2024()));
        when(memberStatusHistoryService.statusOn("M-003", LocalDate.of(2024, 12, 31)))
                .thenReturn(MemberStatus.INACTIVE);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.saveRequest("M-003", examDto())
        );

        assertEquals(NOT_ACTIVE_AT_EXAM, exception.getMessage());
    }

    /**
     * The mirror image: Retired today, but the history has them Active on exam day, so
     * this validation must not be what stops the request. It goes on to fail a later
     * check, which is the point - it got past MMS02.
     */
    @Test
    void saveRequestShouldAcceptWhenHistorySaysActiveOnExamDateEvenIfNotActiveNow() {
        Member member = new Member();
        member.setMemberId("M-004");
        member.setStatus(MemberStatus.RETIRED);
        member.setMembershipStartDate(LocalDate.of(2018, 1, 1));

        when(memberRepository.findByMemberId("M-004")).thenReturn(Optional.of(member));
        when(examMasterRepository.findById(2024)).thenReturn(Optional.of(exam2024()));
        when(memberStatusHistoryService.statusOn("M-004", LocalDate.of(2024, 12, 31)))
                .thenReturn(MemberStatus.ACTIVE);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.saveRequest("M-004", examDto())
        );

        assertNotEquals(NOT_ACTIVE_AT_EXAM, exception.getMessage());
    }

    /**
     * Joining after the exam is the same failure by another route - there was no
     * membership to be Active in on the day that counts.
     */
    @Test
    void saveRequestShouldRejectWhenMembershipStartedAfterTheExam() {
        Member member = new Member();
        member.setMemberId("M-005");
        member.setStatus(MemberStatus.ACTIVE);
        member.setMembershipStartDate(LocalDate.of(2025, 6, 1));

        when(memberRepository.findByMemberId("M-005")).thenReturn(Optional.of(member));
        when(examMasterRepository.findById(2024)).thenReturn(Optional.of(exam2024()));
        when(memberStatusHistoryService.statusOn("M-005", LocalDate.of(2024, 12, 31)))
                .thenReturn(MemberStatus.ACTIVE);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.saveRequest("M-005", examDto())
        );

        assertEquals(NOT_ACTIVE_AT_EXAM, exception.getMessage());
    }
}
