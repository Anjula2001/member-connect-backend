package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.NommineChangeRequests;
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

    public List<NommineChangeRequestDTO> nommineChangeRequestFindService() {
        List<NommineChangeRequests> requests = nominneChangeRequestRepo.findAll();
        return modelMapper.map(requests, new TypeToken<List<NommineChangeRequestDTO>>() {}.getType());
    }

    public NommineChangeRequestDTO getNommineChangeRequestById(Integer id) {
        Optional<NommineChangeRequests> optionalEntity = nominneChangeRequestRepo.findById(id);
        return optionalEntity
                .map(entity -> modelMapper.map(entity, NommineChangeRequestDTO.class))
                .orElse(null);
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

        NommineChangeRequests saved = nominneChangeRequestRepo.save(entity);
        return modelMapper.map(saved, NommineChangeRequestDTO.class);
    }

    /** MMC18: "Once submitted, the user cannot edit the record." */
    public NommineChangeRequestDTO updateNommineChange(Integer id, NommineChangeRequestDTO dto) {
        NommineChangeRequests existing = nominneChangeRequestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Nominee change request not found: " + id));

        statusPolicy.assertEditable(existing.getStatus());

        String requestNo = existing.getRequestNo();
        LocalDate requestedDate = existing.getRequestedDate();
        ApplicationStatus status = existing.getStatus();

        modelMapper.map(dto, existing);

        existing.setId(id);
        existing.setRequestNo(requestNo);
        existing.setRequestedDate(requestedDate);
        existing.setStatus(status);

        return modelMapper.map(nominneChangeRequestRepo.save(existing), NommineChangeRequestDTO.class);
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

        return modelMapper.map(saved, NommineChangeRequestDTO.class);
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
