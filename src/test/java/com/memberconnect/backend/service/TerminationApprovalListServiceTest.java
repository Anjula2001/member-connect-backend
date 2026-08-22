package com.memberconnect.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memberconnect.backend.dto.TerminationApprovalListDTO;
import com.memberconnect.backend.dto.TerminationRequestDecisionDTO;
import com.memberconnect.backend.enums.TerminationApprovalListStatus;
import com.memberconnect.backend.enums.TerminationRequestStatus;
import com.memberconnect.backend.model.TerminationApprovalList;
import com.memberconnect.backend.model.TerminationRequest;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.TerminationApprovalListRepository;
import com.memberconnect.backend.repository.TerminationRequestRepository;

/**
 * Unit tests for MMT09 - processing a Termination Approval List (SRS Section 2.2.7).
 *
 * The behaviour under test is that the board's decisions are applied as one
 * unit: every request in the list gets exactly one verdict, validation happens
 * before any write, and a list is only ever processed once. Plain Mockito, no
 * Spring context and no database, matching TerminationReasonValidationTest.
 */
@ExtendWith(MockitoExtension.class)
class TerminationApprovalListServiceTest {

    private static final String LIST_ID = "TAL-1000";

    @Mock private TerminationApprovalListRepository approvalListRepository;
    @Mock private BoardmeetingRepository boardMeetingRepository;
    @Mock private TerminationRequestRepository terminationRequestRepository;
    @Mock private TerminationService terminationService;

    @Mock private AuditService auditService;


    @InjectMocks private TerminationApprovalListService service;

    // --- helpers ---------------------------------------------------------

    private TerminationRequest request(String requestNo) {
        return request(requestNo, TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
    }

    /** A request already sitting on a list, remembering where it came from. */
    private TerminationRequest request(String requestNo, TerminationRequestStatus previousStatus) {
        TerminationRequest request = new TerminationRequest();
        request.setRequestNo(requestNo);
        request.setStatus(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
        request.setPreviousStatus(previousStatus);
        return request;
    }

    private TerminationApprovalList listWith(String... requestNos) {
        TerminationApprovalList entity = new TerminationApprovalList();
        entity.setListId(LIST_ID);
        entity.setStatus(TerminationApprovalListStatus.CREATED);
        entity.setBoardMeetingDate(LocalDate.of(2026, 8, 10));
        for (String requestNo : requestNos) {
            entity.getRequests().add(request(requestNo));
        }
        return entity;
    }

    private TerminationRequestDecisionDTO decision(String requestNo, String verdict, String reason) {
        TerminationRequestDecisionDTO dto = new TerminationRequestDecisionDTO();
        dto.setRequestNo(requestNo);
        dto.setDecision(verdict);
        dto.setRejectReason(reason);
        return dto;
    }

    private TerminationApprovalListDTO payload(TerminationRequestDecisionDTO... decisions) {
        TerminationApprovalListDTO dto = new TerminationApprovalListDTO();
        dto.setRequestDecisions(List.of(decisions));
        return dto;
    }

    private void listExists(TerminationApprovalList entity) {
        when(approvalListRepository.findByListIdWithRequests(LIST_ID)).thenReturn(Optional.of(entity));
    }

    private void listSaves(TerminationApprovalList entity) {
        when(approvalListRepository.save(entity)).thenReturn(entity);
    }

    // --- the mixed list, which is what the old implementation got wrong ---

    @Test
    void appliesEachDecisionIndividuallyOnAMixedList() {
        TerminationApprovalList entity = listWith("T-1", "T-2", "T-3");
        listExists(entity);
        listSaves(entity);

        TerminationApprovalListDTO result = service.processApprovalList(
                LIST_ID,
                payload(
                        decision("T-1", "Approve", null),
                        decision("T-2", "Reject", "Loan not settled"),
                        decision("T-3", "Approve", null)
                )
        );

        verify(terminationService).approveRequest("T-1");
        verify(terminationService).rejectRequest("T-2", "Loan not settled");
        verify(terminationService).approveRequest("T-3");

        assertThat(result.getApprovedCount()).isEqualTo(2);
        assertThat(result.getRejectedCount()).isEqualTo(1);
    }

    @Test
    void recordsAMixedListAsMixedRatherThanLettingOneVerdictWin() {
        TerminationApprovalList entity = listWith("T-1", "T-2");
        listExists(entity);
        listSaves(entity);

        service.processApprovalList(
                LIST_ID,
                payload(
                        decision("T-1", "Approve", null),
                        decision("T-2", "Reject", "Documents incomplete")
                )
        );

        assertThat(entity.getDecision()).isEqualTo("Mixed");
    }

    @Test
    void recordsAUnanimousListWithThatVerdict() {
        TerminationApprovalList approved = listWith("T-1", "T-2");
        listExists(approved);
        listSaves(approved);

        service.processApprovalList(
                LIST_ID,
                payload(decision("T-1", "Approve", null), decision("T-2", "Approve", null))
        );

        assertThat(approved.getDecision()).isEqualTo("Approve");
        assertThat(approved.getStatus()).isEqualTo(TerminationApprovalListStatus.PROCESSED);
        assertThat(approved.getProcessedAt()).isNotNull();
    }

    // --- validation happens before anything is written -------------------

    @Test
    void refusesTheWholeListWhenARejectionHasNoReason() {
        TerminationApprovalList entity = listWith("T-1", "T-2");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(
                        decision("T-1", "Approve", null),
                        decision("T-2", "Reject", "   ")
                )
        )).hasMessageContaining("rejection reason is required for T-2");

        // The approvable request must not have been touched either - a partially
        // applied board meeting is exactly the failure this design removes.
        verifyNoInteractions(terminationService);
        verify(approvalListRepository, never()).save(entity);
        assertThat(entity.getStatus()).isEqualTo(TerminationApprovalListStatus.CREATED);
    }

    @Test
    void refusesWhenARequestInTheListHasNoDecision() {
        TerminationApprovalList entity = listWith("T-1", "T-2");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(decision("T-1", "Approve", null))
        )).hasMessageContaining("No board decision supplied for: T-2");

        verifyNoInteractions(terminationService);
        assertThat(entity.getStatus()).isEqualTo(TerminationApprovalListStatus.CREATED);
    }

    @Test
    void refusesADecisionForARequestOutsideTheList() {
        TerminationApprovalList entity = listWith("T-1");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(
                        decision("T-1", "Approve", null),
                        decision("T-9", "Approve", null)
                )
        )).hasMessageContaining("not part of approval list");

        verifyNoInteractions(terminationService);
    }

    @Test
    void refusesDuplicateDecisionsForTheSameRequest() {
        TerminationApprovalList entity = listWith("T-1");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(
                        decision("T-1", "Approve", null),
                        decision("T-1", "Reject", "Changed our mind")
                )
        )).hasMessageContaining("Duplicate board decision supplied for T-1");

        verifyNoInteractions(terminationService);
    }

    @Test
    void refusesAnUnrecognisedVerdict() {
        TerminationApprovalList entity = listWith("T-1");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(decision("T-1", "Defer", null))
        )).hasMessageContaining("must be Approve or Reject");

        verifyNoInteractions(terminationService);
    }

    @Test
    void refusesAnEmptyDecisionSet() {
        TerminationApprovalList entity = listWith("T-1");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(LIST_ID, payload()))
                .hasMessageContaining("A board decision is required");

        verifyNoInteractions(terminationService);
    }

    // --- a board meeting is recorded once --------------------------------

    @Test
    void refusesToReprocessAnAlreadyProcessedList() {
        TerminationApprovalList entity = listWith("T-1");
        entity.setStatus(TerminationApprovalListStatus.PROCESSED);
        entity.setProcessedBy("Head Office User");
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(decision("T-1", "Approve", null))
        )).hasMessageContaining("has already been processed");

        verifyNoInteractions(terminationService);
    }

    // --- meeting date ----------------------------------------------------

    @Test
    void fallsBackToTheScheduledDateWhenNoActualMeetingDateIsSupplied() {
        TerminationApprovalList entity = listWith("T-1");
        listExists(entity);
        listSaves(entity);

        service.processApprovalList(LIST_ID, payload(decision("T-1", "Approve", null)));

        assertThat(entity.getActualMeetingDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void keepsTheActualMeetingDateWhenTheBoardMetOnADifferentDay() {
        TerminationApprovalList entity = listWith("T-1");
        listExists(entity);
        listSaves(entity);

        TerminationApprovalListDTO dto = payload(decision("T-1", "Approve", null));
        dto.setActualMeetingDate(LocalDate.of(2026, 8, 17));

        service.processApprovalList(LIST_ID, dto);

        assertThat(entity.getActualMeetingDate()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    // --- MMT07 delete ----------------------------------------------------

    @Test
    void deleteRestoresEachRequestToTheStatusItActuallyHeld() {
        TerminationApprovalList entity = new TerminationApprovalList();
        entity.setListId(LIST_ID);
        entity.setStatus(TerminationApprovalListStatus.CREATED);
        entity.getRequests().add(request("T-1", TerminationRequestStatus.SUBMITTED_FOR_APPROVAL));
        entity.getRequests().add(request("T-2", TerminationRequestStatus.REJECTED));
        listExists(entity);

        service.deleteApprovalList(LIST_ID);

        // MMT07: "rolled back to the Submitted for Approval status or Rejected
        // status depend on what status it was originally". A previously rejected
        // request must not come back as if it had never been refused.
        assertThat(entity.getRequests().get(0).getStatus())
                .isEqualTo(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
        assertThat(entity.getRequests().get(1).getStatus())
                .isEqualTo(TerminationRequestStatus.REJECTED);
        verify(approvalListRepository).delete(entity);
    }

    @Test
    void deleteFallsBackToSubmittedForRowsListedBeforePreviousStatusExisted() {
        TerminationApprovalList entity = new TerminationApprovalList();
        entity.setListId(LIST_ID);
        entity.setStatus(TerminationApprovalListStatus.CREATED);
        TerminationRequest legacy = request("T-1", null);
        entity.getRequests().add(legacy);
        listExists(entity);

        service.deleteApprovalList(LIST_ID);

        assertThat(legacy.getStatus()).isEqualTo(TerminationRequestStatus.SUBMITTED_FOR_APPROVAL);
    }

    @Test
    void addingToAListRecordsWhereTheRequestCameFrom() {
        TerminationRequest rejected = new TerminationRequest();
        rejected.setRequestNo("T-9");
        rejected.setStatus(TerminationRequestStatus.REJECTED);

        when(terminationRequestRepository.findByRequestNo("T-9")).thenReturn(Optional.of(rejected));
        when(boardMeetingRepository.findById(7L)).thenReturn(Optional.of(boardMeeting()));
        when(approvalListRepository.save(any(TerminationApprovalList.class)))
                .thenAnswer(i -> i.getArgument(0));

        TerminationApprovalListDTO dto = new TerminationApprovalListDTO();
        dto.setRequestNos(List.of("T-9"));
        dto.setBoardMeetingId(7L);

        service.createApprovalList(dto);

        assertThat(rejected.getStatus()).isEqualTo(TerminationRequestStatus.ADDED_TO_APPROVAL_LIST);
        assertThat(rejected.getPreviousStatus()).isEqualTo(TerminationRequestStatus.REJECTED);
    }

    private com.memberconnect.backend.model.BoardMeeting boardMeeting() {
        com.memberconnect.backend.model.BoardMeeting meeting =
                new com.memberconnect.backend.model.BoardMeeting();
        meeting.setId(7L);
        meeting.setScheduledDate(LocalDate.of(2026, 8, 10));
        return meeting;
    }

    @Test
    void refusesToProcessAnEmptyList() {
        TerminationApprovalList entity = listWith();
        listExists(entity);

        assertThatThrownBy(() -> service.processApprovalList(
                LIST_ID,
                payload(decision("T-1", "Approve", null))
        )).hasMessageContaining("has no requests to process");

        verify(terminationService, never()).approveRequest(anyString());
    }
}
