package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.NameChangeRequest;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.NameChangeRequestRepo;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NameChangeRequstServices {

    @Autowired
    private NameChangeRequestRepo nameChangeRequestRepo;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RequestNumberGenerator requestNumberGenerator;

    @Autowired
    private ProfileChangeStatusPolicy statusPolicy;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MemberRepository memberRepository;

    public List<NameChangeRequestDTO> NameChangeRequestgetAll() {
        return nameChangeRequestRepo.findAll().stream()
                .map(this::toDtoWithMemberDetails)
                .toList();
    }

    public NameChangeRequestDTO getRequestById(Integer id) {
        return nameChangeRequestRepo.findById(id)
                .map(this::toDtoWithMemberDetails)
                .orElse(null);
    }

    /**
     * MMC05's "Member Details" block - Member ID, Name with Initials and NIC - belongs
     * to the member, so it is resolved on read rather than duplicated onto the request.
     */
    private NameChangeRequestDTO toDtoWithMemberDetails(NameChangeRequest entity) {
        NameChangeRequestDTO dto = modelMapper.map(entity, NameChangeRequestDTO.class);

        if (entity.getMemberId() != null) {
            memberRepository.findByMemberId(entity.getMemberId()).ifPresent(member -> {
                dto.setMemberFullName(member.getFullName());
                dto.setMemberNameWithInitials(member.getNameWithInitials());
                dto.setMemberNic(member.getNic());
            });
        }

        return dto;
    }

    /**
     * MMC05 submit: stamps the Request ID and the requested date, and puts the record
     * on "Submitted for Approval".
     *
     * All three were previously the caller's problem, which is why no name change
     * request had ever been given a Request ID or a date — the screen simply posted
     * the four name fields. Deciding them here means they cannot be forged from the
     * client either.
     */
    public NameChangeRequestDTO addNameChangeRequestService(NameChangeRequestDTO dto) {
        NameChangeRequest entity = modelMapper.map(dto, NameChangeRequest.class);

        entity.setNameChangeRequestID(null);
        entity.setRequestNo(nextRequestNo());
        entity.setRequestedDate(LocalDate.now());
        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);

        snapshotCurrentValues(entity);

        NameChangeRequest saved = nameChangeRequestRepo.save(entity);
        return toDtoWithMemberDetails(saved);
    }

    /**
     * Resolves the member and records the "Current Value" section against the request.
     *
     * memberId here is the membership number (MEM-2026-001), not the Member table's
     * primary key. The entry screen was sending the numeric id it had in the URL, which
     * matched no member at all: the request saved with member_id "1", and the list could
     * not resolve a name, NIC or location for it. Failing loudly when the member cannot
     * be found stops that going unnoticed again.
     */
    private void snapshotCurrentValues(NameChangeRequest entity) {
        String memberId = entity.getMemberId();
        if (memberId == null || memberId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A membership number is required to raise a name change request.");
        }

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No member found with membership number " + memberId
                                + ". Raise the request from the member's profile."));

        // MMC05: the request can only be raised against an active membership.
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Name changes can only be requested for an active member. "
                            + memberId + " is " + member.getStatus() + ".");
        }

        entity.setOldTitle(member.getTitle());
        entity.setOldFullName(member.getFullName());
        entity.setOldNameAsInPayroll(member.getNameAsInPayroll());
        entity.setOldNameWithInitials(member.getNameWithInitials());

        if (entity.getSubmissionLocation() == null || entity.getSubmissionLocation().isBlank()) {
            entity.setSubmissionLocation(
                    member.getSubmissionLocation() != null
                            ? member.getSubmissionLocation()
                            : member.getEducationalDistrict());
        }
    }

    /**
     * MMC05: "Once submitted, the user cannot edit the record." The policy refuses
     * anything that has already left the draft state, so this now only serves records
     * that were never submitted.
     */
    public NameChangeRequestDTO updateNameChangeRequestService(Integer id, NameChangeRequestDTO dto) {
        NameChangeRequest existing = nameChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Name change request not found: " + id));

        String requestNo = existing.getRequestNo();
        String memberId = existing.getMemberId();
        LocalDate requestedDate = existing.getRequestedDate();
        ApplicationStatus previousStatus = existing.getStatus();

        modelMapper.map(dto, existing);

        existing.setNameChangeRequestID(id);
        existing.setRequestNo(requestNo);
        existing.setMemberId(memberId);
        existing.setRequestedDate(requestedDate);

        resubmit(existing, previousStatus);

        return toDtoWithMemberDetails(nameChangeRequestRepo.save(existing));
    }

    /**
     * Puts an edited request back into the approval queue.
     *
     * MMC05 does not allow editing a submitted record at all - it is retired as Inactive
     * and a new request raised. In-place editing is enabled at the product owner's
     * direction, so the safeguard is here instead: an edited request returns to Submitted
     * for Approval and loses any previous decision or approval-list placement, rather
     * than keeping a stamp that no longer describes its contents. The Current Value
     * snapshot is retaken so the board compares against the member as they stand now.
     */
    private void resubmit(NameChangeRequest entity, ApplicationStatus previousStatus) {
        snapshotCurrentValues(entity);

        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);
        entity.setProcessedBy(null);
        entity.setProcessedAt(null);

        if (previousStatus != ApplicationStatus.SUBMITTED_FOR_APPROVAL) {
            auditService.recordStatusChange(
                    AuditService.MODULE_NAME_CHANGE,
                    entity.getMemberId(),
                    entity.getRequestNo(),
                    previousStatus,
                    ApplicationStatus.SUBMITTED_FOR_APPROVAL
            );
        }
    }

    /**
     * MMC07: the only status change available from View Mode is Inactive, from
     * Submitted for Approval or Rejected, and only for a user with Inactive rights.
     */
    public NameChangeRequestDTO updateStatus(Integer id, ApplicationStatus target) {
        NameChangeRequest existing = nameChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Name change request not found: " + id));

        ApplicationStatus previous = existing.getStatus();
        statusPolicy.assertManualStatusChange(previous, target);
        existing.setStatus(target);

        NameChangeRequest saved = nameChangeRequestRepo.save(existing);

        // "An audit record will be created against the Member Record for all the
        // changes done" - section 3.1.1.
        auditService.recordStatusChange(
                AuditService.MODULE_NAME_CHANGE,
                saved.getMemberId(),
                saved.getRequestNo(),
                previous,
                target
        );

        return toDtoWithMemberDetails(saved);
    }

    public String deleteNameChangeRequestService(Integer id) {
        NameChangeRequest existing = nameChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Name change request not found: " + id));

        // Deletion of a submitted request is allowed at the product owner's direction;
        // the audit row keeps it traceable. See the note in BasicProfileChangeRequestServices.
        auditService.recordStatusChange(
                AuditService.MODULE_NAME_CHANGE,
                existing.getMemberId(),
                existing.getRequestNo(),
                existing.getStatus(),
                null
        );

        nameChangeRequestRepo.deleteById(id);
        return "Deleted successfully";
    }

    private String nextRequestNo() {
        String prefix = requestNumberGenerator.prefixFor(ProfileChangeType.NAME);
        return requestNumberGenerator.next(
                ProfileChangeType.NAME,
                nameChangeRequestRepo.findFirstByRequestNoStartingWithOrderByRequestNoDesc(prefix)
                        .map(NameChangeRequest::getRequestNo)
        );
    }
}
