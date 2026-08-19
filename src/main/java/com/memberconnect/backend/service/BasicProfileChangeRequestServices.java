package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.BasicProfileChangeRequestDTO;
import com.memberconnect.backend.dto.ProfileChangeDecisionDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.BasicProfileChangeRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.BasicProfileChangeRequestRepo;
import com.memberconnect.backend.repository.MemberRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional
public class BasicProfileChangeRequestServices {

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private BasicProfileChangeRequestRepo basicProfileChangeRequestRepo;

    @Autowired
    private RequestNumberGenerator requestNumberGenerator;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProfileChangeStatusPolicy statusPolicy;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NotificationService notificationService;

    public List<BasicProfileChangeRequestDTO> getBasicProfileChangeRequests(){
        List<BasicProfileChangeRequest> basicProfileChangeRequests = basicProfileChangeRequestRepo.findAll();
        return modelMapper.map(basicProfileChangeRequests,new TypeToken<List<BasicProfileChangeRequestDTO>>(){}.getType());
    }

    public BasicProfileChangeRequestDTO getRequestById(Integer id) {
        return basicProfileChangeRequestRepo.findById(id)
                .map(this::toDtoWithMemberDetails)
                .orElse(null);
    }

    /**
     * MMC01's "Member Details" block — Member ID, Name with Initials and NIC — is shown
     * on the request screen but belongs to the member, so it is resolved on read rather
     * than duplicated onto the request row.
     */
    private BasicProfileChangeRequestDTO toDtoWithMemberDetails(BasicProfileChangeRequest entity) {
        BasicProfileChangeRequestDTO dto = modelMapper.map(entity, BasicProfileChangeRequestDTO.class);

        if (entity.getMemberId() != null) {
            memberRepository.findByMemberId(entity.getMemberId()).ifPresent(member -> {
                dto.setMemberFullName(member.getFullName());
                dto.setMemberNameWithInitials(member.getNameWithInitials());
                dto.setMemberNic(member.getNic());
            });
        }

        return dto;
    }

    public String saveBasicProfileChangeRequest(BasicProfileChangeRequestDTO basicProfileChangeRequestDTO){
        BasicProfileChangeRequest entity = modelMapper.map(basicProfileChangeRequestDTO, BasicProfileChangeRequest.class);
        stampOnSubmit(entity);
        basicProfileChangeRequestRepo.save(entity);
        return "success";
    }

    public String saveWithDocument(BasicProfileChangeRequestDTO dto, org.springframework.web.multipart.MultipartFile file) {
        BasicProfileChangeRequest entity = modelMapper.map(dto, BasicProfileChangeRequest.class);
        stampOnSubmit(entity);
        handleFileUpload(entity, file);
        basicProfileChangeRequestRepo.save(entity);
        return "success";
    }

    // ── Approve / Reject (MMC04) ─────────────────────────────────────────────

    /**
     * Approves or rejects a Basic Profile Change Request.
     *
     * One transaction does the whole thing: set the status, copy the approved values
     * onto the Member Profile, write the audit row and notify the member.
     *
     * The screen used to orchestrate this from the browser as two independent calls —
     * update the member, then update the request — which had two faults. It was not
     * atomic, so a failure between the calls left the member changed but the request
     * still pending. And it passed the request's memberId ("MEM-2026-001") to an
     * endpoint keyed by the Member table's numeric primary key, so Number(memberId)
     * evaluated to NaN and every approval failed with "Failed to convert value of type
     * java.lang.String to required type java.lang.Long; For input string: NaN".
     * Resolving the member by membership number here removes both faults.
     */
    public BasicProfileChangeRequestDTO decide(Integer id, ProfileChangeDecisionDTO decision) {
        if (decision == null || decision.getDecision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A decision is required.");
        }

        BasicProfileChangeRequest request = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile change request not found: " + id));

        statusPolicy.assertDecidable(request.getStatus());

        Member member = memberRepository.findByMemberId(request.getMemberId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The member for this request could not be found: " + request.getMemberId()));

        boolean approving = decision.getDecision() == ProfileChangeDecisionDTO.Decision.APPROVE;

        if (!approving) {
            String reason = statusPolicy.requireRejectReason(decision.getRejectReason());

            request.setStatus(ApplicationStatus.REJECTED);
            request.setRejectReason(reason);
            stampProcessed(request);
            BasicProfileChangeRequest saved = basicProfileChangeRequestRepo.save(request);

            // MMC04: on reject the Member Profile is deliberately left untouched.
            auditService.record(
                    AuditService.MODULE_BASIC_PROFILE_CHANGE,
                    member.getId(),
                    "REJECTED",
                    null,
                    null,
                    "Request " + saved.getRequestNo() + " rejected: " + reason
            );
            notificationService.sendProfileChangeRejected(
                    member, ProfileChangeType.BASIC_PROFILE, saved.getRequestNo(), reason);

            return modelMapper.map(saved, BasicProfileChangeRequestDTO.class);
        }

        Map<String, Object> before = snapshot(member);
        applyToMember(request, member);
        Map<String, Object> after = snapshot(member);
        memberRepository.save(member);

        request.setStatus(ApplicationStatus.APPROVED);
        request.setRejectReason(null);
        stampProcessed(request);
        BasicProfileChangeRequest saved = basicProfileChangeRequestRepo.save(request);

        auditService.recordFieldChanges(
                AuditService.MODULE_BASIC_PROFILE_CHANGE,
                member.getId(),
                "APPROVED",
                before,
                after,
                "Request " + saved.getRequestNo() + " approved"
        );
        notificationService.sendProfileChangeApproved(
                member, ProfileChangeType.BASIC_PROFILE, saved.getRequestNo());

        return modelMapper.map(saved, BasicProfileChangeRequestDTO.class);
    }

    /**
     * Copies the approved new values onto the Member Profile.
     *
     * A blank value leaves the member's current value alone rather than wiping it: the
     * three optional fields (private telephone, mobile number, email address) are
     * legitimately empty on a request that did not set them.
     */
    private void applyToMember(BasicProfileChangeRequest request, Member member) {
        if (request.getNewBirthDate() != null) {
            member.setDateOfBirth(request.getNewBirthDate());
        }
        setIfPresent(request.getNewNIC(), member::setNic);
        setIfPresent(request.getNewPermanentPrivateAddress(), member::setPermanentPrivateAddress);
        setIfPresent(request.getNewPrivateTelephone(), member::setPrivateTelephone);
        setIfPresent(request.getNewMobileNumber(), member::setMobileNumber);
        setIfPresent(request.getNewEmailAddress(), member::setEmailAddress);
        setIfPresent(request.getNewDesignation(), member::setDesignation);

        // The request stores these three as text; the Member holds them as enums.
        parseEnum(Gender.class, request.getNewGender(), "gender")
                .ifPresent(member::setGender);
        parseEnum(Language.class, request.getNewPreferredLanguage(), "preferred language")
                .ifPresent(member::setPreferredLanguage);
        parseEnum(NatureOfOccupation.class, request.getNewNatureOfOccupation(), "nature of occupation")
                .ifPresent(member::setNatureOfOccupation);
    }

    /** The fields this request type can change, captured for the audit diff. */
    private Map<String, Object> snapshot(Member member) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("dateOfBirth", String.valueOf(member.getDateOfBirth()));
        values.put("nic", String.valueOf(member.getNic()));
        values.put("gender", String.valueOf(member.getGender()));
        values.put("preferredLanguage", String.valueOf(member.getPreferredLanguage()));
        values.put("permanentPrivateAddress", String.valueOf(member.getPermanentPrivateAddress()));
        values.put("privateTelephone", String.valueOf(member.getPrivateTelephone()));
        values.put("mobileNumber", String.valueOf(member.getMobileNumber()));
        values.put("emailAddress", String.valueOf(member.getEmailAddress()));
        values.put("designation", String.valueOf(member.getDesignation()));
        values.put("natureOfOccupation", String.valueOf(member.getNatureOfOccupation()));
        return values;
    }

    private void setIfPresent(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    /**
     * Parses a stored text value into its enum. An unrecognised value is refused rather
     * than skipped: silently dropping it would mark the request approved while leaving
     * the member on the old value, with nothing to show why.
     */
    private <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, value.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This request has an unrecognised " + label + " and cannot be approved: " + value);
        }
    }

    /** Records what was removed, so a deleted request is still traceable. */
    private void auditForDeletion(BasicProfileChangeRequest request) {
        if (request.getMemberId() == null) {
            return;
        }
        memberRepository.findByMemberId(request.getMemberId()).ifPresent(member ->
                auditService.record(
                        AuditService.MODULE_BASIC_PROFILE_CHANGE,
                        member.getId(),
                        "DELETED",
                        null,
                        null,
                        "Request " + request.getRequestNo() + " (" + request.getStatus() + ") was deleted"
                ));
    }

    private void stampProcessed(BasicProfileChangeRequest request) {
        request.setProcessedBy(statusPolicy.currentUsername());
        request.setProcessedAt(LocalDateTime.now());
    }

    // ── Create / update ──────────────────────────────────────────────────────

    /**
     * MMC01 submit: the Request ID, the requested date and the status are decided here,
     * not by the caller.
     *
     * Previously all three came off the request body, which is why no basic profile
     * request had ever carried a Request ID and why createdDate — the column that was
     * supposed to hold the requested date — was null on every row: nothing on the
     * create path ever set it.
     */
    private void stampOnSubmit(BasicProfileChangeRequest entity) {
        entity.setId(null);
        entity.setRequestNo(nextRequestNo());
        entity.setRequestedDate(LocalDate.now());
        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);

        snapshotCurrentValues(entity);
    }

    /**
     * Resolves the member and records the "Current Value" section against the request.
     *
     * memberId here is the membership number (MEM-2026-001), not the Member table's
     * primary key. The entry screen was sending the numeric id it had in the URL, which
     * matched no member at all: the request saved with member_id "2", and the list could
     * not resolve a name, NIC or location for it. Failing loudly when the member cannot
     * be found stops that going unnoticed again.
     */
    private void snapshotCurrentValues(BasicProfileChangeRequest entity) {
        String memberId = entity.getMemberId();
        if (memberId == null || memberId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A membership number is required to raise a profile change request.");
        }

        Member member = memberRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No member found with membership number " + memberId
                                + ". Raise the request from the member's profile."));

        // MMC01: the request can only be raised against an active membership.
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Profile changes can only be requested for an active member. "
                            + memberId + " is " + member.getStatus() + ".");
        }

        entity.setOldBirthDate(member.getDateOfBirth());
        entity.setOldNIC(member.getNic());
        entity.setOldGender(member.getGender() == null ? null : member.getGender().name());
        entity.setOldPreferredLanguage(
                member.getPreferredLanguage() == null ? null : member.getPreferredLanguage().name());
        entity.setOldPermanentPrivateAddress(member.getPermanentPrivateAddress());
        entity.setOldPrivateTelephone(member.getPrivateTelephone());
        entity.setOldMobileNumber(member.getMobileNumber());
        entity.setOldEmailAddress(member.getEmailAddress());
        entity.setOldDesignation(member.getDesignation());
        entity.setOldNatureOfOccupation(
                member.getNatureOfOccupation() == null ? null : member.getNatureOfOccupation().name());

        // Falls back to the member's own district so the Location filter has a value even
        // when the caller does not supply one.
        if (entity.getSubmissionLocation() == null || entity.getSubmissionLocation().isBlank()) {
            entity.setSubmissionLocation(
                    member.getSubmissionLocation() != null
                            ? member.getSubmissionLocation()
                            : member.getEducationalDistrict());
        }
    }

    private String nextRequestNo() {
        ProfileChangeType type = ProfileChangeType.BASIC_PROFILE;
        String prefix = requestNumberGenerator.prefixFor(type);
        return requestNumberGenerator.next(
                type,
                basicProfileChangeRequestRepo.findFirstByRequestNoStartingWithOrderByRequestNoDesc(prefix)
                        .map(BasicProfileChangeRequest::getRequestNo)
        );
    }

    public BasicProfileChangeRequestDTO updateProfileRequest(Integer id, BasicProfileChangeRequestDTO dto) {
        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile change request not found: " + id));

        Identity identity = Identity.of(existingEntity);
        modelMapper.map(dto, existingEntity);
        identity.restoreOnto(existingEntity, id);
        resubmit(existingEntity, identity.status);

        BasicProfileChangeRequest updatedEntity = basicProfileChangeRequestRepo.save(existingEntity);
        return toDtoWithMemberDetails(updatedEntity);
    }

    /**
     * The parts of a request an edit must never move: its identity and when it was first
     * raised. ModelMapper overwrites the whole target, so these are captured before the
     * map and put back after it.
     */
    private record Identity(String requestNo, String memberId, LocalDate requestedDate, ApplicationStatus status) {
        static Identity of(BasicProfileChangeRequest e) {
            return new Identity(e.getRequestNo(), e.getMemberId(), e.getRequestedDate(), e.getStatus());
        }

        void restoreOnto(BasicProfileChangeRequest e, Integer id) {
            e.setId(id);
            e.setRequestNo(requestNo);
            e.setMemberId(memberId);
            e.setRequestedDate(requestedDate);
        }
    }

    /**
     * Puts an edited request back into the approval queue.
     *
     * MMC01 does not allow editing a submitted record at all — it is retired as Inactive
     * and a new request raised. In-place editing is enabled at the product owner's
     * direction, so the safeguard is here instead: a request that is edited returns to
     * Submitted for Approval and loses any previous decision, rather than keeping an
     * Approved or Rejected stamp that no longer describes its contents. The Current Value
     * snapshot is retaken so the approver compares against the member as they stand now.
     */
    private void resubmit(BasicProfileChangeRequest entity, ApplicationStatus previousStatus) {
        snapshotCurrentValues(entity);

        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);
        entity.setProcessedBy(null);
        entity.setProcessedAt(null);

        if (previousStatus != ApplicationStatus.SUBMITTED_FOR_APPROVAL) {
            auditService.recordStatusChange(
                    AuditService.MODULE_BASIC_PROFILE_CHANGE,
                    entity.getMemberId(),
                    entity.getRequestNo(),
                    previousStatus,
                    ApplicationStatus.SUBMITTED_FOR_APPROVAL
            );
        }
    }

    /**
     * MMC03 View Mode status change — Inactive only, from Submitted for Approval or
     * Rejected, and only for a user holding Inactive rights. Approve and reject go
     * through {@link #decide}, which also updates the Member Profile.
     */
    public BasicProfileChangeRequestDTO updateStatus(Integer id, ApplicationStatus status) {
        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile change request not found: " + id));

        ApplicationStatus previous = existingEntity.getStatus();
        statusPolicy.assertManualStatusChange(previous, status);
        existingEntity.setStatus(status);

        BasicProfileChangeRequest saved = basicProfileChangeRequestRepo.save(existingEntity);

        auditService.recordStatusChange(
                AuditService.MODULE_BASIC_PROFILE_CHANGE,
                saved.getMemberId(),
                saved.getRequestNo(),
                previous,
                status
        );

        return modelMapper.map(saved, BasicProfileChangeRequestDTO.class);
    }

    public BasicProfileChangeRequestDTO updateWithDocument(Integer id, BasicProfileChangeRequestDTO dto, org.springframework.web.multipart.MultipartFile file) {
        BasicProfileChangeRequest existingEntity = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile change request not found: " + id));

        Identity identity = Identity.of(existingEntity);

        // Save current file storage path in case we replace it
        String oldFileStoragePath = existingEntity.getDocumentStoragePath();

        modelMapper.map(dto, existingEntity);
        identity.restoreOnto(existingEntity, id);
        resubmit(existingEntity, identity.status);

        if (file != null && !file.isEmpty()) {
            // Delete old file if present
            deleteFileIfExists(oldFileStoragePath);
            // Handle new upload
            handleFileUpload(existingEntity, file);
        } else if (dto.getDocumentStoragePath() == null || dto.getDocumentStoragePath().isBlank()) {
            // If the frontend explicitly cleared the storage path, delete the physical file too
            deleteFileIfExists(oldFileStoragePath);
            existingEntity.setDocumentStoragePath(null);
            existingEntity.setDocumentFileName(null);
            existingEntity.setDocumentFileType(null);
            existingEntity.setDocumentFileSize(null);
            existingEntity.setDocumentType(null);
        } else {
            // Keep the old file path if no new file is provided and path is not cleared
            existingEntity.setDocumentStoragePath(oldFileStoragePath);
        }

        BasicProfileChangeRequest updatedEntity = basicProfileChangeRequestRepo.save(existingEntity);
        return toDtoWithMemberDetails(updatedEntity);
    }

    public String deleteProfileRequest(Integer id) {
        BasicProfileChangeRequest request = basicProfileChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile change request not found: " + id));

        // Deleting a submitted request goes beyond MMC01/MMC03, which retire one by
        // setting it Inactive because it forms part of the approval trail. Deletion is
        // allowed here at the product owner's direction, so an audit row is written
        // first: the request row goes, but the fact that it existed does not.
        auditForDeletion(request);

        // Delete associated physical file
        deleteFileIfExists(request.getDocumentStoragePath());

        basicProfileChangeRequestRepo.deleteById(id);
        return "Successfully deleted request";
    }

    private void handleFileUpload(BasicProfileChangeRequest entity, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            String uploadDir = "uploads";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String originalFileName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String uniqueFileName = java.util.UUID.randomUUID().toString() + fileExtension;
            java.nio.file.Path targetLocation = uploadPath.resolve(uniqueFileName);
            java.nio.file.Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            entity.setDocumentStoragePath(uniqueFileName);
            entity.setDocumentFileName(originalFileName);
            entity.setDocumentFileType(file.getContentType());
            entity.setDocumentFileSize(file.getSize());
            if (!dtoHasDocumentType(entity)) {
                entity.setDocumentType("SUPPORTING_DOC");
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not store file: " + e.getMessage(), e);
        }
    }

    private boolean dtoHasDocumentType(BasicProfileChangeRequest entity) {
        return entity.getDocumentType() != null && !entity.getDocumentType().isBlank();
    }

    private void deleteFileIfExists(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads").resolve(fileName).normalize();
            java.nio.file.Files.deleteIfExists(filePath);
        } catch (java.io.IOException e) {
            System.err.println("Warning: could not delete file: " + e.getMessage());
        }
    }
}
