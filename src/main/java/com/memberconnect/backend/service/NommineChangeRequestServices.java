package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.NommineChangeRequests;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.NominneChangeRequestRepo;
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
public class NommineChangeRequestServices {

    @Autowired
    public NominneChangeRequestRepo nominneChangeRequestRepo;

    @Autowired
    public ModelMapper modelMapper;

    @Autowired
    private RequestNumberGenerator requestNumberGenerator;

    @Autowired
    private ProfileChangeStatusPolicy statusPolicy;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MemberRepository memberRepository;

    public List<NommineChangeRequestDTO> nommineChangeRequestFindService() {
        return nominneChangeRequestRepo.findAll().stream()
                .map(this::toDtoWithMemberDetails)
                .toList();
    }

    public NommineChangeRequestDTO getNommineChangeRequestById(Integer id) {
        return nominneChangeRequestRepo.findById(id)
                .map(this::toDtoWithMemberDetails)
                .orElse(null);
    }

    /**
     * MMC18's "Member Details" block - Member ID, Name with Initials and NIC - belongs
     * to the member, so it is resolved on read rather than duplicated onto the request.
     */
    private NommineChangeRequestDTO toDtoWithMemberDetails(NommineChangeRequests entity) {
        NommineChangeRequestDTO dto = modelMapper.map(entity, NommineChangeRequestDTO.class);

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
     * MMC18 submit: stamps the Request ID and the requested date, and puts the record
     * on "Submitted for Approval".
     *
     * The id is cleared first because the entry screen used to post an edit to this
     * same create endpoint with the id in the body, relying on save() behaving as an
     * upsert. Update now has its own path, so a create here is always a create.
     */
    public NommineChangeRequestDTO NommineChangeRequestaddService(NommineChangeRequestDTO dto) {
        NommineChangeRequests entity = modelMapper.map(dto, NommineChangeRequests.class);

        entity.setId(null);
        entity.setRequestNo(nextRequestNo());
        entity.setRequestedDate(LocalDate.now());
        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);

        snapshotCurrentValues(entity);

        NommineChangeRequests saved = nominneChangeRequestRepo.save(entity);
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
     *
     * The nominee's identification number lives on the member as identificationNumber -
     * distinct from nic, which is the member's own.
     */
    private void snapshotCurrentValues(NommineChangeRequests entity) {
        String memberId = entity.getMemberId();
        if (memberId == null || memberId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A membership number is required to raise a nominee change request.");
        }

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No member found with membership number " + memberId
                                + ". Raise the request from the member's profile."));

        // MMC18: the request can only be raised against an active membership.
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nominee changes can only be requested for an active member. "
                            + memberId + " is " + member.getStatus() + ".");
        }

        entity.setOldNommineName(member.getNomineeFullName());
        entity.setOldRelationship(member.getNomineeRelationship());
        entity.setOldNic(member.getIdentificationNumber());
        entity.setOldAddress(member.getNomineeAddress());

        if (entity.getSubmissionLocation() == null || entity.getSubmissionLocation().isBlank()) {
            entity.setSubmissionLocation(
                    member.getSubmissionLocation() != null
                            ? member.getSubmissionLocation()
                            : member.getEducationalDistrict());
        }
    }

    /** MMC18: "Once submitted, the user cannot edit the record." */
    public NommineChangeRequestDTO updateNommineChange(Integer id, NommineChangeRequestDTO dto) {
        NommineChangeRequests existing = nominneChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Nominee change request not found: " + id));

        String requestNo = existing.getRequestNo();
        String memberId = existing.getMemberId();
        LocalDate requestedDate = existing.getRequestedDate();
        ApplicationStatus previousStatus = existing.getStatus();

        modelMapper.map(dto, existing);

        existing.setId(id);
        existing.setRequestNo(requestNo);
        existing.setMemberId(memberId);
        existing.setRequestedDate(requestedDate);

        resubmit(existing, previousStatus);

        return toDtoWithMemberDetails(nominneChangeRequestRepo.save(existing));
    }

    /**
     * Puts an edited request back into the approval queue.
     *
     * MMC18 does not allow editing a submitted record at all - it is retired as Inactive
     * and a new request raised. In-place editing is enabled at the product owner's
     * direction, so the safeguard is here instead: an edited request returns to Submitted
     * for Approval and loses any previous decision or approval-list placement, rather
     * than keeping a stamp that no longer describes its contents. The Current Value
     * snapshot is retaken so the board compares against the member as they stand now.
     */
    private void resubmit(NommineChangeRequests entity, ApplicationStatus previousStatus) {
        snapshotCurrentValues(entity);

        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);
        entity.setProcessedBy(null);
        entity.setProcessedAt(null);

        if (previousStatus != ApplicationStatus.SUBMITTED_FOR_APPROVAL) {
            auditService.recordStatusChange(
                    AuditService.MODULE_NOMINEE_CHANGE,
                    entity.getMemberId(),
                    entity.getRequestNo(),
                    previousStatus,
                    ApplicationStatus.SUBMITTED_FOR_APPROVAL
            );
        }
    }

    /** MMC20: Inactive only, from Submitted for Approval or Rejected, with rights. */
    public NommineChangeRequestDTO updateStatus(Integer id, ApplicationStatus target) {
        NommineChangeRequests existing = nominneChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Nominee change request not found: " + id));

        ApplicationStatus previous = existing.getStatus();
        statusPolicy.assertManualStatusChange(previous, target);
        existing.setStatus(target);

        NommineChangeRequests saved = nominneChangeRequestRepo.save(existing);

        // "An audit record will be created against the Member Record for all the
        // changes done" - section 5.1.1.
        auditService.recordStatusChange(
                AuditService.MODULE_NOMINEE_CHANGE,
                saved.getMemberId(),
                saved.getRequestNo(),
                previous,
                target
        );

        return toDtoWithMemberDetails(saved);
    }

    public String deleteNommineChangeRequestService(Integer id) {
        NommineChangeRequests existing = nominneChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Nominee change request not found: " + id));

        // Deletion of a submitted request is allowed at the product owner's direction;
        // the audit row keeps it traceable. See the note in BasicProfileChangeRequestServices.
        auditService.recordStatusChange(
                AuditService.MODULE_NOMINEE_CHANGE,
                existing.getMemberId(),
                existing.getRequestNo(),
                existing.getStatus(),
                null
        );

        nominneChangeRequestRepo.deleteById(id);
        return "Deleted successfully";
    }

    private String nextRequestNo() {
        String prefix = requestNumberGenerator.prefixFor(ProfileChangeType.NOMINEE);
        return requestNumberGenerator.next(
                ProfileChangeType.NOMINEE,
                nominneChangeRequestRepo.findFirstByRequestNoStartingWithOrderByRequestNoDesc(prefix)
                        .map(NommineChangeRequests::getRequestNo)
        );
    }
}
