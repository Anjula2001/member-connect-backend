package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.NameChangeRequest;
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

    public List<NameChangeRequestDTO> NameChangeRequestgetAll() {
        List<NameChangeRequest> nameChangeRequests = nameChangeRequestRepo.findAll();
        return modelMapper.map(nameChangeRequests, new TypeToken<List<NameChangeRequestDTO>>() {}.getType());
    }

    public NameChangeRequestDTO getRequestById(Integer id) {
        Optional<NameChangeRequest> optionalEntity = nameChangeRequestRepo.findById(id);
        return optionalEntity
                .map(entity -> modelMapper.map(entity, NameChangeRequestDTO.class))
                .orElse(null);
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

        NameChangeRequest saved = nameChangeRequestRepo.save(entity);
        return modelMapper.map(saved, NameChangeRequestDTO.class);
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

        statusPolicy.assertEditable(existing.getStatus());

        String requestNo = existing.getRequestNo();
        LocalDate requestedDate = existing.getRequestedDate();
        ApplicationStatus status = existing.getStatus();

        modelMapper.map(dto, existing);

        existing.setNameChangeRequestID(id);
        existing.setRequestNo(requestNo);
        existing.setRequestedDate(requestedDate);
        existing.setStatus(status);

        return modelMapper.map(nameChangeRequestRepo.save(existing), NameChangeRequestDTO.class);
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

        return modelMapper.map(saved, NameChangeRequestDTO.class);
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
