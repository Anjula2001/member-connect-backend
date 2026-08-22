package com.memberconnect.backend.service;

import com.memberconnect.backend.config.CurrentUserService;
import com.memberconnect.backend.dto.MemberRelocationHandoffDTO;
import com.memberconnect.backend.dto.MemberTransferDto;
import com.memberconnect.backend.event.MemberTransferApprovedEvent;
import com.memberconnect.backend.enums.MemberTransferStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.model.WorkingLocation;
import com.memberconnect.backend.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MemberTransferService {

    private final MemberTransferRepository memberTransferRepository;
    private final MemberRepository memberRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkingLocationTypeRepository workingLocationTypeRepository;
    private final EducationalDistrictRepository educationalDistrictRepository;
    private final EducationalZoneRepository educationalZoneRepository;
    private final WorkingLocationRepository workingLocationRepository;
    private final DesignationRepository designationRepository;
    private final NatureOfOccupationRepository natureOfOccupationRepository;

    public MemberTransferService(
            MemberTransferRepository memberTransferRepository,
            MemberRepository memberRepository,
            CurrentUserService currentUserService,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            WorkingLocationTypeRepository workingLocationTypeRepository,
            EducationalDistrictRepository educationalDistrictRepository,
            EducationalZoneRepository educationalZoneRepository,
            WorkingLocationRepository workingLocationRepository,
            DesignationRepository designationRepository,
            NatureOfOccupationRepository natureOfOccupationRepository) {
        this.memberTransferRepository = memberTransferRepository;
        this.memberRepository = memberRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.workingLocationTypeRepository = workingLocationTypeRepository;
        this.educationalDistrictRepository = educationalDistrictRepository;
        this.educationalZoneRepository = educationalZoneRepository;
        this.workingLocationRepository = workingLocationRepository;
        this.designationRepository = designationRepository;
        this.natureOfOccupationRepository = natureOfOccupationRepository;
    }

    /**
     * Every transfer request the caller may see (MMC28).
     *
     * A District Office user is pinned to their own district; Head Office, Board
     * Secretary and Super Admin see them all. The filter is applied here rather than
     * on the screen so a restricted caller cannot widen it by asking.
     */
    public List<MemberTransferRequest> getAllRequests() {
        return getAllRequests(null);
    }

    public List<MemberTransferRequest> getAllRequests(List<String> requestedLocations) {
        CurrentUserService.LocationScope scope =
                currentUserService.resolveLocationScope(requestedLocations);

        if (scope.showsNothing()) {
            return List.of();
        }

        return memberTransferRepository.findAll().stream()
                .filter(request -> currentUserService.matchesScope(
                        scope, request.getSubmissionLocation()))
                .toList();
    }

    // Method to get a specific member transfer request by ID
    public MemberTransferRequest getRequestById(Long id) {
        MemberTransferRequest request = memberTransferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member transfer request not found"));

        assertVisible(request);

        return request;
    }

    /**
     * Refuses a request that belongs to another District Office.
     *
     * Opening one by its id has to be checked separately from the list: filtering the
     * list alone would leave the record reachable to anyone who guessed an id.
     */
    private void assertVisible(MemberTransferRequest request) {
        CurrentUserService.LocationScope scope = currentUserService.resolveLocationScope(null);

        if (scope.showsNothing()
                || !currentUserService.matchesScope(scope, request.getSubmissionLocation())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This member transfer request belongs to another District Office");
        }
    }

    /**
     * A member may only have one transfer request awaiting approval. A second one
     * would compete with the first: whichever is approved last overwrites the
     * member's working location with its own snapshot-era values.
     *
     * Only SUBMITTEDFORAPPROVAL blocks. An APPROVED transfer is finished work, so
     * the member can be transferred again later; REJECTED and INACTIVE never block.
     */
    private void assertNoRequestAwaitingApproval(Member member) {
        if (member == null || member.getMemberId() == null) {
            return;
        }

        memberTransferRepository
                .findFirstByMember_MemberIdAndStatus(
                        member.getMemberId(), MemberTransferStatus.SUBMITTEDFORAPPROVAL)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Member " + member.getMemberId() + " already has transfer request "
                                    + existing.getRequestId() + " awaiting approval."
                                    + " That request must be approved or rejected first.");
                });
    }

    // Returns the request awaiting approval for this member, or null when there is none
    public MemberTransferRequest findRequestAwaitingApproval(String memberId) {
        if (!StringUtils.hasText(memberId)) {
            return null;
        }

        return memberTransferRepository
                .findFirstByMember_MemberIdAndStatus(
                        memberId, MemberTransferStatus.SUBMITTEDFORAPPROVAL)
                .orElse(null);
    }

    // Method to submit a new member transfer request
    public MemberTransferRequest submitRequest(MemberTransferDto dto) {
        MemberTransferRequest request = new MemberTransferRequest();

        applyDtoToEntity(dto, request);

        assertNoRequestAwaitingApproval(request.getMember());

        // Snapshot the member's current values at time of request creation
        if (request.getMember() != null) {
            com.memberconnect.backend.model.Member m = request.getMember();
            request.setCurrentDesignation(m.getDesignation());
            request.setCurrentNatureOfOccupation(
                    m.getNatureOfOccupation() != null ? m.getNatureOfOccupation().name() : null);
            request.setCurrentWorkingLocationType(m.getWorkingLocationType());
            request.setCurrentEducationalDistrict(m.getEducationalDistrict());
            request.setCurrentEducationalZone(m.getEducationalZone());
            request.setCurrentWorkingLocation(m.getWorkingLocation());
            request.setCurrentWorkingLocationAddress(m.getWorkingLocationAddress());
            request.setCurrentComputerNoInPayslip(m.getComputerNoInPayslip());
            request.setCurrentSalaryPayingOffice(m.getSalaryPayingOffice());
        }

        request.setRequestId(generateMemberTransferRequestId());
        request.setStatus(MemberTransferStatus.SUBMITTEDFORAPPROVAL);
        request.setSubmissionLocation(resolveSubmissionLocationFor(request.getMember()));

        return memberTransferRepository.save(request);
    }

    /**
     * The office a request belongs to: the member's own administering office, or the
     * district of the user raising it when the member carries none.
     */
    private String resolveSubmissionLocationFor(Member member) {
        if (member != null) {
            String memberLocation = member.getSubmissionLocation();
            if (memberLocation != null && !memberLocation.isBlank()) {
                return memberLocation;
            }
        }

        return currentUserService.restrictedToLocation();
    }

    public void deleteRequest(Long id) {
        memberTransferRepository.deleteById(id);
    }

    private void applyDtoToEntity(MemberTransferDto dto, MemberTransferRequest request) {

        if (dto.getMemberId() != null) {
            Member member = memberRepository.findById(Long.valueOf(dto.getMemberId()))
                    .orElseThrow(() -> new RuntimeException("Member not found"));
            request.setMember(member);
        }

        if (dto.getRequestedDate() != null) {
            request.setRequestedDate(dto.getRequestedDate());
        }

        if (dto.getNewWorkingLocationTypeId() != null) {
            request.setNewWorkingLocationType(
                    workingLocationTypeRepository.findById(dto.getNewWorkingLocationTypeId())
                            .orElseThrow(() -> new RuntimeException("Working location type not found")));
        }

        if (dto.getNewEducationalDistrictId() != null) {
            request.setNewEducationalDistrict(
                    educationalDistrictRepository.findById(dto.getNewEducationalDistrictId())
                            .orElseThrow(() -> new RuntimeException("Educational district not found")));
        }

        if (dto.getNewEducationalZoneId() != null) {
            request.setNewEducationalZone(
                    educationalZoneRepository.findById(dto.getNewEducationalZoneId())
                            .orElseThrow(() -> new RuntimeException("Educational zone not found")));
        }

        if (dto.getNewWorkingLocationId() != null) {
            WorkingLocation workingLocation = workingLocationRepository.findById(dto.getNewWorkingLocationId())
                    .orElseThrow(() -> new RuntimeException("Working location not found"));

            request.setNewWorkingLocation(workingLocation);

            request.setNewWorkingLocationAddress(workingLocation.getAddress());
            request.setNewSalaryPayingOffice(workingLocation.getSalaryPayingOffice());
        }

        if (dto.getNewDesignationId() != null) {
            request.setNewDesignation(
                    designationRepository.findById(dto.getNewDesignationId())
                            .orElseThrow(() -> new RuntimeException("Designation not found")));
        }

        if (dto.getNewNatureOfOccupationId() != null) {
            request.setNewNatureOfOccupation(
                    natureOfOccupationRepository.findById(dto.getNewNatureOfOccupationId())
                            .orElseThrow(() -> new RuntimeException("Nature of occupation not found")));
        }

        if (StringUtils.hasText(dto.getNewComputerNoInPayslip())) {
            request.setNewComputerNoInPayslip(dto.getNewComputerNoInPayslip().trim());
        }
    }

    //Generates a unique request ID 
    private String generateMemberTransferRequestId() {
        long nextNumber = memberTransferRepository.count() + 1;
        String candidate = String.format("MTR-%03d", nextNumber);

        while (memberTransferRepository.existsByRequestId(candidate)) {
            nextNumber++;
            candidate = String.format("MTR-%03d", nextNumber);
        }

        return candidate;
    }

    public MemberTransferRequest findRequestByIdOrRequestId(String key) {
        // Try finding by requestId first
        java.util.Optional<MemberTransferRequest> request = memberTransferRepository.findByRequestId(key);
        if (request.isPresent()) {
            return request.get();
        }
        // Try parsing as Long database ID
        try {
            Long id = Long.parseLong(key);
            return memberTransferRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Member transfer request not found with ID: " + key));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Member transfer request not found: " + key);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public MemberTransferRequest approveRequest(String key) {
        MemberTransferRequest request = findRequestByIdOrRequestId(key);

        if (request.getStatus() == MemberTransferStatus.APPROVED) {
            throw new RuntimeException("Request is already approved");
        }

        // Update request status
        request.setStatus(MemberTransferStatus.APPROVED);

        // Read before the writes below overwrite it. There is no persisted "Keep
        // Current District" flag to consult: when that box is ticked the request carries
        // the member's existing district as its new one, so comparing before with after
        // is what tells the two cases apart.
        String districtBefore = request.getMember() != null
                ? request.getMember().getEducationalDistrict()
                : null;

        // Update Member profile with requested changes
        Member member = request.getMember();
        if (member != null) {
            if (request.getNewDesignation() != null) {
                member.setDesignation(request.getNewDesignation().getName());
            }
            if (request.getNewNatureOfOccupation() != null) {
                String name = request.getNewNatureOfOccupation().getName().toUpperCase();
                try {
                    member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.valueOf(name));
                } catch (IllegalArgumentException e) {
                    if (name.startsWith("PERM")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.PERMANENT);
                    } else if (name.startsWith("PROB")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.PROBATION);
                    } else if (name.startsWith("TEMP")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.TEMPORARY);
                    } else if (name.startsWith("CASU")) {
                        member.setNatureOfOccupation(com.memberconnect.backend.enums.NatureOfOccupation.CASUAL);
                    }
                }
            }
            if (request.getNewWorkingLocationType() != null) {
                member.setWorkingLocationType(request.getNewWorkingLocationType().getName());
            }
            if (request.getNewEducationalDistrict() != null) {
                member.setEducationalDistrict(request.getNewEducationalDistrict().getName());
            }
            if (request.getNewEducationalZone() != null) {
                member.setEducationalZone(request.getNewEducationalZone().getName());
            }
            if (request.getNewWorkingLocation() != null) {
                member.setWorkingLocation(request.getNewWorkingLocation().getName());
            }
            if (request.getNewWorkingLocationAddress() != null) {
                member.setWorkingLocationAddress(request.getNewWorkingLocationAddress());
            }
            if (request.getNewComputerNoInPayslip() != null) {
                member.setComputerNoInPayslip(request.getNewComputerNoInPayslip());
            }
            if (request.getNewSalaryPayingOffice() != null) {
                member.setSalaryPayingOffice(request.getNewSalaryPayingOffice());
            }
            memberRepository.save(member);
        }

        MemberTransferRequest saved = memberTransferRepository.save(request);

        notifyDecision(saved, MemberTransferStatus.APPROVED);

        String districtAfter = member != null ? member.getEducationalDistrict() : null;

        if (districtChanged(districtBefore, districtAfter)) {
            // Consumed AFTER_COMMIT by the Finance and Loan listeners, so an unreachable
            // module cannot roll back an approval that has already been applied to the
            // member's profile (MMC30).
            eventPublisher.publishEvent(new MemberTransferApprovedEvent(
                    member.getMemberId(),
                    saved.getRequestId(),
                    districtBefore,
                    districtAfter
            ));
        }

        return saved;
    }

    /**
     * Whether an approved transfer actually moved the member to a different District.
     *
     * A transfer that leaves the district alone - including one where "Keep Current
     * District" was ticked - moves no loans and no accounts, so nothing is sent.
     */
    private boolean districtChanged(String before, String after) {
        if (after == null || after.isBlank()) {
            return false;
        }
        if (before == null || before.isBlank()) {
            // The member had no district recorded and now has one: that is a move into a
            // district, and the receiving module needs to know about it.
            return true;
        }
        return !before.trim().equalsIgnoreCase(after.trim());
    }

    /**
     * The payload both downstream modules receive. Read at send time from the request
     * rather than copied through the event, matching the termination handoff.
     */
    public MemberRelocationHandoffDTO buildRelocationHandoff(MemberTransferApprovedEvent event) {
        MemberTransferRequest request = findRequestByIdOrRequestId(event.requestNo());
        Member member = request.getMember();

        MemberRelocationHandoffDTO handoff = new MemberRelocationHandoffDTO();
        handoff.setRequestNo(event.requestNo());
        handoff.setMemberId(event.memberId());
        handoff.setFromDistrict(event.fromDistrict());
        handoff.setToDistrict(event.toDistrict());
        handoff.setApprovedOn(java.time.LocalDate.now());

        if (member != null) {
            handoff.setMemberName(member.getNameWithInitials() != null
                    ? member.getNameWithInitials()
                    : member.getFullName());
            handoff.setNic(member.getNic());
        }

        if (request.getNewWorkingLocation() != null) {
            handoff.setNewWorkingLocation(request.getNewWorkingLocation().getName());
        }

        return handoff;
    }

    /**
     * Emails the member about a decision on their transfer (MMC30).
     *
     * Called after the save, so the member is only told about a decision that was
     * actually recorded. Best-effort: NotificationService swallows delivery failures,
     * and a request with no member linked is skipped rather than throwing - an
     * undeliverable email must not undo an approval that has already been applied to
     * the profile.
     */
    private void notifyDecision(MemberTransferRequest request, MemberTransferStatus status) {
        Member member = request.getMember();
        if (member == null || !StringUtils.hasText(member.getMemberId())) {
            return;
        }

        String memberId = member.getMemberId();
        String requestNo = request.getRequestId();

        if (status == MemberTransferStatus.APPROVED) {
            notificationService.notifyMemberTransferApproved(
                    memberId,
                    requestNo,
                    request.getNewWorkingLocation() != null
                            ? request.getNewWorkingLocation().getName()
                            : null,
                    request.getNewDesignation() != null
                            ? request.getNewDesignation().getName()
                            : null);
        } else if (status == MemberTransferStatus.REJECTED) {
            notificationService.notifyMemberTransferRejected(
                    memberId, requestNo, request.getDecisionReason());
        }
    }

    /**
     * Changes a request's status from View Mode.
     *
     * Only the two transitions the spec allows are possible: a request awaiting
     * approval, or one that was rejected, may be made Inactive. Nothing else moves -
     * an approved transfer has already been written onto the member's profile, and
     * an inactive request is closed.
     */
    @org.springframework.transaction.annotation.Transactional
    public MemberTransferRequest changeRequestStatus(String key, String newStatusStr) {
        MemberTransferRequest request = findRequestByIdOrRequestId(key);

        MemberTransferStatus newStatus;
        try {
            newStatus = MemberTransferStatus.valueOf(
                    newStatusStr == null ? "" : newStatusStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid status: " + newStatusStr);
        }

        MemberTransferStatus currentStatus = request.getStatus();
        if (currentStatus == null) {
            throw new RuntimeException("Current request status is unrecognized");
        }

        if (currentStatus == newStatus) {
            return request;
        }

        if (!isStatusTransitionAllowed(currentStatus, newStatus)) {
            throw new RuntimeException(
                    "Cannot change status from " + currentStatus + " to " + newStatus);
        }

        request.setStatus(newStatus);

        return memberTransferRepository.save(request);
    }

    private boolean isStatusTransitionAllowed(
            MemberTransferStatus current, MemberTransferStatus next) {
        switch (current) {
            case SUBMITTEDFORAPPROVAL:
            case REJECTED:
                return next == MemberTransferStatus.INACTIVE;
            default:
                return false;
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public MemberTransferRequest rejectRequest(String key, String reason) {
        MemberTransferRequest request = findRequestByIdOrRequestId(key);

        request.setStatus(MemberTransferStatus.REJECTED);
        request.setDecisionReason(reason);

        MemberTransferRequest saved = memberTransferRepository.save(request);

        notifyDecision(saved, MemberTransferStatus.REJECTED);

        return saved;
    }
}