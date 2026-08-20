package com.memberconnect.backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.memberconnect.backend.dto.TerminationReasonMasterDto;
import com.memberconnect.backend.model.TerminationReason;
import com.memberconnect.backend.repository.TerminationReasonRepository;

/**
 * Maintenance of the Termination Reasons Master (MMT01) - the list behind the
 * "Termination due to" dropdown on a termination request.
 *
 * Separate from TerminationService for the same reason UniversityMasterService is
 * separate from UniversityScholarshipService: that class is the request workflow,
 * this is the reference data behind it, with a different audience and a different
 * right. TerminationService keeps ownership of reading the master for the dropdown
 * (getTerminationReasonOptions) and of resolving a submitted reason onto a request.
 *
 * Add and edit only. Nothing here deletes: a reason can already be referenced by an
 * approved termination request, and an approval recorded years ago must keep pointing
 * at a reason that still exists. Retiring one is "active = false", which
 * TerminationService.applyTerminationReason() already understands - it refuses an
 * inactive reason as a new choice while accepting it on a request that already holds
 * it, so retiring a reason can neither block nor silently rewrite live work.
 */
@Service
public class TerminationReasonMasterService {

    private final TerminationReasonRepository terminationReasonRepository;

    public TerminationReasonMasterService(TerminationReasonRepository terminationReasonRepository) {
        this.terminationReasonRepository = terminationReasonRepository;
    }

    /**
     * Every reason, retired ones included. The dropdown endpoint filters to active;
     * this one must not, or a deactivated reason would vanish from the only screen
     * that could reactivate it.
     */
    public List<TerminationReasonMasterDto> getReasons() {
        return terminationReasonRepository.findAllByOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public TerminationReasonMasterDto createReason(TerminationReasonMasterDto request) {
        String code = requireCode(request.getCode());
        if (terminationReasonRepository.existsByCodeIgnoreCase(code)) {
            throw badRequest("A termination reason with this code already exists");
        }

        TerminationReason reason = new TerminationReason();
        reason.setCode(code);
        reason.setName(requireName(request.getName()));
        reason.setActive(request.getActive() == null || request.getActive());
        reason.setDisplayOrder(request.getDisplayOrder() != null
                ? requireDisplayOrder(request.getDisplayOrder())
                : nextDisplayOrder());
        return toDto(terminationReasonRepository.save(reason));
    }

    /**
     * Name, display order and active are editable; the code is not.
     *
     * The code is this row's stable identifier - the seeder matches on it, and it is
     * what a future integration would key on - so a submitted code is ignored here
     * rather than rejected. Enforced on the server, not merely by a read-only input:
     * the screen disables the field, but that is not what makes it safe.
     */
    public TerminationReasonMasterDto updateReason(Long id, TerminationReasonMasterDto request) {
        TerminationReason reason = terminationReasonRepository.findById(id)
                .orElseThrow(() -> notFound("Termination reason not found"));

        reason.setName(requireName(request.getName()));
        if (request.getActive() != null) {
            reason.setActive(request.getActive());
        }
        if (request.getDisplayOrder() != null) {
            reason.setDisplayOrder(requireDisplayOrder(request.getDisplayOrder()));
        }
        return toDto(terminationReasonRepository.save(reason));
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Codes are stored upper-case so that the uniqueness check and the column's own
     * unique constraint agree. existsByCodeIgnoreCase would otherwise pass "medical"
     * against an existing "MEDICAL" only for the database to reject the insert.
     */
    private String requireCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw badRequest("Code is required");
        }
        return value.trim().toUpperCase();
    }

    private String requireName(String value) {
        if (!StringUtils.hasText(value)) {
            throw badRequest("Reason is required");
        }
        return value.trim();
    }

    private Integer requireDisplayOrder(Integer displayOrder) {
        if (displayOrder < 0) {
            throw badRequest("Display order cannot be negative");
        }
        return displayOrder;
    }

    /** Appends a new reason to the end of the dropdown rather than at position null. */
    private int nextDisplayOrder() {
        return terminationReasonRepository.findAll().stream()
                .map(TerminationReason::getDisplayOrder)
                .filter(order -> order != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private TerminationReasonMasterDto toDto(TerminationReason reason) {
        TerminationReasonMasterDto dto = new TerminationReasonMasterDto();
        dto.setId(reason.getId());
        dto.setCode(reason.getCode());
        dto.setName(reason.getName());
        dto.setActive(reason.isActive());
        dto.setDisplayOrder(reason.getDisplayOrder());
        return dto;
    }
}
