package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.DeathDonationRequestDTO;
import com.memberconnect.backend.dto.DeathDonationResponseDTO;
import com.memberconnect.backend.dto.MarkIncompleteDTO;
import com.memberconnect.backend.enums.DeathDonationStatus;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.DeathDonationDocument;
import com.memberconnect.backend.model.DeathDonationRelative;
import com.memberconnect.backend.model.DeathDonationRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.DeathDonationDocumentRepository;
import com.memberconnect.backend.repository.DeathDonationRelativeRepository;
import com.memberconnect.backend.repository.DeathDonationRequestRepository;
import com.memberconnect.backend.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
@Transactional
public class DeathDonationService {

    @Autowired
    private DeathDonationRequestRepository requestRepository;

    @Autowired
    private DeathDonationRelativeRepository relativeRepository;

    @Autowired
    private DeathDonationDocumentRepository documentRepository;

    @Autowired
    private MemberRepository memberRepository;

   
    /**
     * Creates a new Death Donation Request and saves it with status = NEW.
     * This is also the "Save" action from the frontend.
     *
     * @param dto  All fields from the frontend form
     * @return     The saved request mapped to a response DTO
     */
    public DeathDonationResponseDTO createRequest(DeathDonationRequestDTO dto) {

        // 1. Find the requesting member – must exist and be ACTIVE
        Member member = findActiveMember(dto.getMemberId());

        // 2. Basic field validation
        validateRequiredFields(dto);

        // 3. requestedDate must not be in the future
        if (dto.getRequestedDate() != null && dto.getRequestedDate().isAfter(LocalDate.now())) {
            throw new RuntimeException("Requested date cannot be in the future");
        }

        // 4. If deceased is a member, deceasedMemberId is required
        if (Boolean.TRUE.equals(dto.getIsDeceasedMember()) &&
                (dto.getDeceasedMemberId() == null || dto.getDeceasedMemberId().isBlank())) {
            throw new RuntimeException("Deceased member ID is required when 'Is Deceased a Member' is YES");
        }

        // 5. Build the entity
        DeathDonationRequest entity = buildEntity(dto, member);
        entity.setStatus(DeathDonationStatus.NEW);

        // 6. Attach relatives
        attachRelatives(entity, dto.getRelatives());

        // 7. Persist
        DeathDonationRequest saved = requestRepository.save(entity);
        return toResponseDTO(saved);
    }

     

    /**
     * Saves an existing NEW request (draft mode).
     * Does NOT change the status; only updates field values.
     *
     * @param id   DB id of the request to update
     * @param dto  Updated form data
     */
    public DeathDonationResponseDTO saveRequest(Long id, DeathDonationRequestDTO dto) {

        DeathDonationRequest entity = findRequest(id);

        // Only NEW requests can be freely edited
        if (entity.getStatus() != DeathDonationStatus.NEW) {
            throw new RuntimeException("Only NEW requests can be edited in draft mode");
        }

        updateEntityFromDto(entity, dto);

        // Replace relatives list
        entity.getRelatives().clear();
        attachRelatives(entity, dto.getRelatives());

        return toResponseDTO(requestRepository.save(entity));
    }

   
    /**
     * Validates the request and moves it to SUBMITTED_FOR_APPROVAL.
     * After this point the form is read-only (except concernsIdentified).
     *
     * @param id   DB id of the request
     * @param dto  Optional latest form data to persist before submitting
     */
    public DeathDonationResponseDTO submitRequest(Long id, DeathDonationRequestDTO dto) {

        DeathDonationRequest entity = findRequest(id);

        // Can only submit from NEW
        if (entity.getStatus() != DeathDonationStatus.NEW) {
            throw new RuntimeException("Only NEW requests can be submitted");
        }

        // If fresh data is provided, merge it in first
        if (dto != null) {
            updateEntityFromDto(entity, dto);
            if (dto.getRelatives() != null) {
                entity.getRelatives().clear();
                attachRelatives(entity, dto.getRelatives());
            }
        }

        // Mandatory field validation before submission
        validateRequiredFields(buildDtoFromEntity(entity));

        // Date cannot be future
        if (entity.getRequestedDate().isAfter(LocalDate.now())) {
            throw new RuntimeException("Requested date cannot be in the future");
        }

       
        long monthsSinceDeath = ChronoUnit.MONTHS.between(entity.getDeceasedDate(), entity.getRequestedDate());
        if (monthsSinceDeath > 3) {
            
        }

        entity.setStatus(DeathDonationStatus.SUBMITTED_FOR_APPROVAL);
        return toResponseDTO(requestRepository.save(entity));
    }


    /**
     * Marks a request as INCOMPLETE and records the reason.
     *
     * @param id  DB id of the request
     * @param dto Contains the reason text
     */
    public DeathDonationResponseDTO markIncomplete(Long id, MarkIncompleteDTO dto) {

        DeathDonationRequest entity = findRequest(id);

        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new RuntimeException("A reason is required to mark a request as incomplete");
        }

        entity.setStatus(DeathDonationStatus.INCOMPLETE);
        entity.setIncompleteReason(dto.getReason());

        return toResponseDTO(requestRepository.save(entity));
    }

   
    /**
     * Changes the status of a request based on specific allowed transitions.
     *
     * @param id DB id of the request
     * @param newStatus The target status
     */
    public DeathDonationResponseDTO changeStatus(Long id, DeathDonationStatus newStatus) {
        DeathDonationRequest entity = findRequest(id);
        DeathDonationStatus currentStatus = entity.getStatus();

        // Enforce allowed transitions:
        boolean allowed = false;

        if (newStatus == DeathDonationStatus.INACTIVE) {
            // Can change to INACTIVE from NEW, INCOMPLETE, SUBMITTED_FOR_APPROVAL, DISTRICT_COMMITTEE, PD_COMMITTEE, REJECTED
            if (currentStatus != DeathDonationStatus.APPROVED) {
                allowed = true; // Assuming "Inactive rights" check would happen at the controller/security layer
            }
        } else if (newStatus == DeathDonationStatus.NEW) {
            // Can change to NEW from INCOMPLETE, SUBMITTED_FOR_APPROVAL, DISTRICT_COMMITTEE, PD_COMMITTEE, REJECTED, INACTIVE
            if (currentStatus != DeathDonationStatus.APPROVED) {
                allowed = true;
            }
        }

        if (!allowed) {
            throw new RuntimeException("Cannot change status from " + currentStatus + " to " + newStatus);
        }

        entity.setStatus(newStatus);
        return toResponseDTO(requestRepository.save(entity));
    }

   

    /**
     * Fetches a single Death Donation Request by its DB id.
     */
    public DeathDonationResponseDTO getRequest(Long id) {
        return toResponseDTO(findRequest(id));
    }

    /**
     * Fetches all Death Donation Requests.
     */
    public List<DeathDonationResponseDTO> getAllRequests() {
        return requestRepository.findAll().stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

  

    /** Find member by id, throw if not found or not ACTIVE */
    private Member findActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found with id: " + memberId));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new RuntimeException("Member is not ACTIVE. Current status: " + member.getStatus());
        }
        return member;
    }

    /** Find a Death Donation Request entity by id or throw */
    private DeathDonationRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Death Donation Request not found with id: " + id));
    }

    /** Build a new entity from a DTO + member */
    private DeathDonationRequest buildEntity(DeathDonationRequestDTO dto, Member member) {
        DeathDonationRequest entity = new DeathDonationRequest();
        entity.setMember(member);
        updateEntityFromDto(entity, dto);
        return entity;
    }

    /** Copy DTO fields onto an existing entity (shared between create and save) */
    private void updateEntityFromDto(DeathDonationRequest entity, DeathDonationRequestDTO dto) {
        if (dto.getRelationshipToDeceased() != null)  entity.setRelationshipToDeceased(dto.getRelationshipToDeceased());
        if (dto.getRequestedDate() != null)            entity.setRequestedDate(dto.getRequestedDate());
        if (dto.getIsDeceasedMember() != null)         entity.setIsDeceasedMember(dto.getIsDeceasedMember());
        if (dto.getDeceasedMemberId() != null)         entity.setDeceasedMemberId(dto.getDeceasedMemberId());
        if (dto.getDeceasedName() != null)             entity.setDeceasedName(dto.getDeceasedName());
        if (dto.getMaidenName() != null)               entity.setMaidenName(dto.getMaidenName());
        if (dto.getDeceasedDate() != null)             entity.setDeceasedDate(dto.getDeceasedDate());
        if (dto.getDeathCertificateNumber() != null)   entity.setDeathCertificateNumber(dto.getDeathCertificateNumber());
        if (dto.getPlaceOfWork() != null)              entity.setPlaceOfWork(dto.getPlaceOfWork());
        if (dto.getConcernsIdentified() != null)       entity.setConcernsIdentified(dto.getConcernsIdentified());
    }

    /** Build a DTO from entity – used internally for validation before submit */
    private DeathDonationRequestDTO buildDtoFromEntity(DeathDonationRequest e) {
        DeathDonationRequestDTO dto = new DeathDonationRequestDTO();
        dto.setRelationshipToDeceased(e.getRelationshipToDeceased());
        dto.setRequestedDate(e.getRequestedDate());
        dto.setIsDeceasedMember(e.getIsDeceasedMember());
        dto.setDeceasedMemberId(e.getDeceasedMemberId());
        dto.setDeceasedName(e.getDeceasedName());
        dto.setDeceasedDate(e.getDeceasedDate());
        dto.setDeathCertificateNumber(e.getDeathCertificateNumber());
        return dto;
    }

    /** Validate mandatory fields (used on save and before submit) */
    private void validateRequiredFields(DeathDonationRequestDTO dto) {
        if (dto.getRelationshipToDeceased() == null || dto.getRelationshipToDeceased().isBlank())
            throw new RuntimeException("Relationship to deceased is required");
        if (dto.getDeceasedName() == null || dto.getDeceasedName().isBlank())
            throw new RuntimeException("Deceased name is required");
        if (dto.getDeceasedDate() == null)
            throw new RuntimeException("Deceased date is required");
        if (dto.getDeathCertificateNumber() == null || dto.getDeathCertificateNumber().isBlank())
            throw new RuntimeException("Death certificate number is required");
    }

    /** Attach relative DTOs to the entity's list */
    private void attachRelatives(DeathDonationRequest entity,
                                  List<DeathDonationRequestDTO.RelativeDTO> relativeDTOs) {
        if (relativeDTOs == null) return;

        for (DeathDonationRequestDTO.RelativeDTO r : relativeDTOs) {
            DeathDonationRelative relative = new DeathDonationRelative();
            relative.setDeathDonationRequest(entity);
            relative.setMemberId(r.getMemberId());
            relative.setRelationshipToDeceased(r.getRelationshipToDeceased());
            relative.setIsAuto(r.getIsAuto() != null && r.getIsAuto());
            entity.getRelatives().add(relative);
        }
    }

    private DeathDonationResponseDTO toResponseDTO(DeathDonationRequest entity) {
        DeathDonationResponseDTO dto = new DeathDonationResponseDTO();

        dto.setId(entity.getId());
        dto.setRequestId(entity.getRequestId());
        dto.setMemberId(entity.getMember().getId());
        dto.setMemberName(entity.getMember().getFullName());
        dto.setRelationshipToDeceased(entity.getRelationshipToDeceased());
        dto.setRequestedDate(entity.getRequestedDate());
        dto.setIsDeceasedMember(entity.getIsDeceasedMember());
        dto.setDeceasedMemberId(entity.getDeceasedMemberId());
        dto.setDeceasedName(entity.getDeceasedName());
        dto.setMaidenName(entity.getMaidenName());
        dto.setDeceasedDate(entity.getDeceasedDate());
        dto.setDeathCertificateNumber(entity.getDeathCertificateNumber());
        dto.setPlaceOfWork(entity.getPlaceOfWork());
        dto.setConcernsIdentified(entity.getConcernsIdentified());
        dto.setStatus(entity.getStatus());
        dto.setIncompleteReason(entity.getIncompleteReason());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Map relatives
        List<DeathDonationResponseDTO.RelativeResponseDTO> relativeDTOs = entity.getRelatives()
                .stream()
                .map(r -> {
                    DeathDonationResponseDTO.RelativeResponseDTO rd = new DeathDonationResponseDTO.RelativeResponseDTO();
                    rd.setId(r.getId());
                    rd.setMemberId(r.getMemberId());
                    rd.setRelationshipToDeceased(r.getRelationshipToDeceased());
                    rd.setIsAuto(r.getIsAuto());
                    return rd;
                })
                .collect(Collectors.toList());
        dto.setRelatives(relativeDTOs);

        // Map documents
        List<DeathDonationResponseDTO.DocumentResponseDTO> docDTOs = entity.getDocuments()
                .stream()
                .map(d -> {
                    DeathDonationResponseDTO.DocumentResponseDTO dd = new DeathDonationResponseDTO.DocumentResponseDTO();
                    dd.setId(d.getId());
                    dd.setDocumentType(d.getDocumentType());
                    dd.setFileName(d.getFileName());
                    dd.setMimeType(d.getMimeType());
                    dd.setMandatory(d.getMandatory());
                    dd.setUploadedAt(d.getUploadedAt());
                    return dd;
                })
                .collect(Collectors.toList());
        dto.setDocuments(docDTOs);

        return dto;
    }
}
