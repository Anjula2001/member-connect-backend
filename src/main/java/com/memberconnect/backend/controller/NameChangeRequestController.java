package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.NameChangeRequstServices;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Name Change Requests (Requirement 02, MMC05-MMC07).
 *
 * The approval-list side of the flow (MMC08-MMC13) lives with the board approval
 * lists, not here.
 */
/**
 * Authorization (MMC05-MMC07). The class-level rule keeps the three specialist roles -
 * Accounts, Scholarship Officer, Death Donation Officer - out of the module; the
 * methods narrow it further.
 *
 * Stage 2 of a name change lives in BoardApprovalListController, which already carries
 * its own rules: Head Office builds and prints the list, Board Secretary records the
 * decision (MMC12) and deletes the list (MMC10).
 *
 * These annotations are the enforcement. The role checks on the screens decide what is
 * shown and are bypassed by calling the API directly.
 */
@RestController
@RequestMapping("/api5/namechange")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE')")
public class NameChangeRequestController {

    @Autowired
    public NameChangeRequstServices nameChangeRequstServices;

    @GetMapping("/getnamechange")
    public List<NameChangeRequestDTO> getNameChangeRequests() {
        return nameChangeRequstServices.NameChangeRequestgetAll();
    }

    @GetMapping("/getnamebyid/{id}")
    public NameChangeRequestDTO getNameChangeRequestsById(@PathVariable Integer id) {
        NameChangeRequestDTO dto = nameChangeRequstServices.getRequestById(id);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Name change request not found: " + id);
        }
        return dto;
    }

    /** @Valid was missing everywhere, so none of the DTO's constraints ever ran. */
    /** MMC05: raised by District Office. Board Secretary decides but never opens one. */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
    @PostMapping("/savenamechange")
    public NameChangeRequestDTO saveNameChangeRequest(@Valid @RequestBody NameChangeRequestDTO dto) {
        return nameChangeRequstServices.addNameChangeRequestService(dto);
    }

    /**
     * Create with a supporting document (MMC05). The document part is optional, so the
     * same screen serves requests with and without one.
     *
     * The JSON travels as a "request" part rather than as form fields, which keeps the
     * DTO's validation working on a multipart submit.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
    @PostMapping(value = "/savenamechangeWithDocument", consumes = {"multipart/form-data"})
    public NameChangeRequestDTO saveNameChangeWithDocument(
            @Valid @RequestPart("request") NameChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return nameChangeRequstServices.saveWithDocument(dto, file);
    }

    /** Update, optionally replacing the supporting document. */
    /**
     * MMC05 forbids editing a submitted record; in-place editing is enabled at the
     * client's direction and restricted to the roles that can decide the request. A
     * District Office user cannot revise what it has already sent to the board.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    @PutMapping(value = "/updatenamechangeWithDocument/{id}", consumes = {"multipart/form-data"})
    public NameChangeRequestDTO updateNameChangeWithDocument(
            @PathVariable Integer id,
            @Valid @RequestPart("request") NameChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return nameChangeRequstServices.updateWithDocument(id, dto, file);
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    @PutMapping("/updatenamechange/{id}")
    public NameChangeRequestDTO updateNameChangeRequest(
            @PathVariable Integer id,
            @Valid @RequestBody NameChangeRequestDTO dto
    ) {
        return nameChangeRequstServices.updateNameChangeRequestService(id, dto);
    }

    /**
     * MMC07 View Mode status change. Separate from the update endpoint because the
     * rules differ: the fields are locked, and the only permitted target is Inactive
     * for a user holding Inactive rights.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    @PutMapping("/updatestatus/{id}")
    public NameChangeRequestDTO updateStatus(
            @PathVariable Integer id,
            @RequestParam("status") ApplicationStatus status
    ) {
        return nameChangeRequstServices.updateStatus(id, status);
    }

    @DeleteMapping("/deletnameChange/{id}")
    public String deleteNameChangeRequest(@PathVariable Integer id) {
        return nameChangeRequstServices.deleteNameChangeRequestService(id);
    }
}
