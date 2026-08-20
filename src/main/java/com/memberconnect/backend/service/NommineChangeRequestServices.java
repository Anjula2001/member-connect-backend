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

    @Autowired
    private S3Service s3Service;

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
        auditService.recordRequestCreated(
                AuditService.MODULE_NOMINEE_CHANGE,
                saved.getMemberId(),
                saved.getRequestNo(),
                saved.getStatus()
        );
        return toDtoWithMemberDetails(saved);
    }

    /**
     * Creates the request and stores its supporting document (MMC18).
     *
     * The document goes to S3 through {@link S3Service}, the same store Basic Profile,
     * Death Donation, Member Death and University Scholarship use. S3Service keeps its
     * own local fallback, so an S3 outage stores the file rather than losing the user's
     * upload.
     */
    public NommineChangeRequestDTO saveWithDocument(
            NommineChangeRequestDTO dto,
            org.springframework.web.multipart.MultipartFile file) {

        NommineChangeRequests entity = modelMapper.map(dto, NommineChangeRequests.class);

        entity.setId(null);
        entity.setRequestNo(nextRequestNo());
        entity.setRequestedDate(LocalDate.now());
        entity.setStatus(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
        entity.setRejectReason(null);

        snapshotCurrentValues(entity);
        handleFileUpload(entity, file);

        NommineChangeRequests saved = nominneChangeRequestRepo.save(entity);
        auditService.recordRequestCreated(
                AuditService.MODULE_NOMINEE_CHANGE,
                saved.getMemberId(),
                saved.getRequestNo(),
                saved.getStatus()
        );
        return toDtoWithMemberDetails(saved);
    }

    /**
     * Updates the request, optionally replacing its document.
     *
     * A new file replaces and deletes the old object. No file plus a blank storage path
     * means "remove the document". No file plus the existing key means "leave it alone" -
     * which is what the screen sends on an ordinary edit, so editing the nominee details
     * does not disturb the uploaded file.
     */
    public NommineChangeRequestDTO updateWithDocument(
            Integer id,
            NommineChangeRequestDTO dto,
            org.springframework.web.multipart.MultipartFile file) {

        NommineChangeRequests existing = nominneChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Nominee change request not found: " + id));

        String requestNo = existing.getRequestNo();
        String memberId = existing.getMemberId();
        LocalDate requestedDate = existing.getRequestedDate();
        ApplicationStatus previousStatus = existing.getStatus();
        // The whole document block has to be captured, not just the key: the DTO the
        // screen sends back carries only documentStoragePath, so mapping it over the
        // entity nulls the file name, type and size. Restoring the key alone left the
        // file downloadable but nameless on the screen.
        String oldStorageKey = existing.getDocumentStoragePath();
        String oldFileName = existing.getDocumentFileName();
        String oldFileType = existing.getDocumentFileType();
        Long oldFileSize = existing.getDocumentFileSize();
        String oldDocumentType = existing.getDocumentType();

        modelMapper.map(dto, existing);

        existing.setId(id);
        existing.setRequestNo(requestNo);
        existing.setMemberId(memberId);
        existing.setRequestedDate(requestedDate);

        resubmit(existing, previousStatus);

        if (file != null && !file.isEmpty()) {
            deleteFileIfExists(oldStorageKey);
            handleFileUpload(existing, file);
        } else if (dto.getDocumentStoragePath() == null || dto.getDocumentStoragePath().isBlank()) {
            deleteFileIfExists(oldStorageKey);
            clearDocument(existing);
        } else {
            existing.setDocumentStoragePath(oldStorageKey);
            existing.setDocumentFileName(oldFileName);
            existing.setDocumentFileType(oldFileType);
            existing.setDocumentFileSize(oldFileSize);
            existing.setDocumentType(oldDocumentType);
        }

        return toDtoWithMemberDetails(nominneChangeRequestRepo.save(existing));
    }

    /** Stores the file in S3 and records its metadata against the request. */
    private void handleFileUpload(
            NommineChangeRequests entity,
            org.springframework.web.multipart.MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            entity.setDocumentStoragePath(s3Service.uploadFile(file));
            entity.setDocumentFileName(file.getOriginalFilename());
            entity.setDocumentFileType(file.getContentType());
            entity.setDocumentFileSize(file.getSize());
            if (entity.getDocumentType() == null || entity.getDocumentType().isBlank()) {
                entity.setDocumentType("SUPPORTING_DOC");
            }
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store the supporting document: " + e.getMessage());
        }
    }

    /**
     * Removes the object from S3. S3Service also clears any local fallback copy. Never
     * throws: failing to delete a file must not roll back the record change.
     */
    private void deleteFileIfExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        s3Service.deleteFile(storageKey);
    }

    private void clearDocument(NommineChangeRequests entity) {
        entity.setDocumentStoragePath(null);
        entity.setDocumentFileName(null);
        entity.setDocumentFileType(null);
        entity.setDocumentFileSize(null);
        entity.setDocumentType(null);
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

        // The supporting document goes with the request; leaving it behind would orphan
        // an object in S3 that nothing references any more.
        deleteFileIfExists(existing.getDocumentStoragePath());

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
