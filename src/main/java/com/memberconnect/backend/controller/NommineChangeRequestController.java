package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.NommineChangeRequestServices;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Nominee Change Requests (Requirement 02, MMC18-MMC20).
 *
 * The base path is "/api/v3" with a leading slash; it was "api/v3", which is a relative
 * mapping and a trap for anyone building URLs from it.
 *
 * The approval-list side of the flow (MMC21-MMC26) lives with the board approval lists.
 */
/**
 * Authorization (MMC18-MMC20). The class-level rule keeps the three specialist roles -
 * Accounts, Scholarship Officer, Death Donation Officer - out of the module; the
 * methods narrow it further.
 *
 * Stage 2 of a nominee change lives in BoardApprovalListController, which already
 * carries its own rules: Head Office builds and prints the list, Board Secretary
 * records the decision (MMC25) and deletes the list (MMC23).
 *
 * These annotations are the enforcement. The role checks on the screens decide what is
 * shown and are bypassed by calling the API directly.
 */
@RestController
@RequestMapping("/api/v3")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE')")
public class NommineChangeRequestController {

    @Autowired
    public NommineChangeRequestServices nommineChangeRequestServices;

    @GetMapping("/getnommine")
    public List<NommineChangeRequestDTO> getNewNommine() {
        return nommineChangeRequestServices.nommineChangeRequestFindService();
    }

    @GetMapping("/getnommineById/{id}")
    public NommineChangeRequestDTO getNewNommineById(@PathVariable Integer id) {
        NommineChangeRequestDTO dto = nommineChangeRequestServices.getNommineChangeRequestById(id);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nominee change request not found: " + id);
        }
        return dto;
    }

    /** MMC18: raised by District Office. Board Secretary decides but never opens one. */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
    @PostMapping("/saveNommine")
    public NommineChangeRequestDTO saveNommineChangeRequest(@Valid @RequestBody NommineChangeRequestDTO dto) {
        return nommineChangeRequestServices.NommineChangeRequestaddService(dto);
    }

    /**
     * Create with a supporting document (MMC18). The document part is optional, so the
     * same screen serves requests with and without one.
     *
     * The JSON travels as a "request" part rather than as form fields, which keeps the
     * DTO's validation working on a multipart submit.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
    @PostMapping(value = "/saveNommineWithDocument", consumes = {"multipart/form-data"})
    public NommineChangeRequestDTO saveNommineWithDocument(
            @Valid @RequestPart("request") NommineChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return nommineChangeRequestServices.saveWithDocument(dto, file);
    }

    /** Update, optionally replacing the supporting document. */
    /**
     * MMC18 forbids editing a submitted record; in-place editing is enabled at the
     * client's direction and restricted to the roles that can decide the request. A
     * District Office user cannot revise what it has already sent to the board.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    @PutMapping(value = "/updateNommineWithDocument/{id}", consumes = {"multipart/form-data"})
    public NommineChangeRequestDTO updateNommineWithDocument(
            @PathVariable Integer id,
            @Valid @RequestPart("request") NommineChangeRequestDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return nommineChangeRequestServices.updateWithDocument(id, dto, file);
    }

    /**
     * The entry screen used to post its edits to /saveNommine with an id in the body,
     * leaving this endpoint unused. It is the update path.
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    @PutMapping("/updateNommine/{id}")
    public NommineChangeRequestDTO updateNommineChangeRequest(
            @PathVariable Integer id,
            @Valid @RequestBody NommineChangeRequestDTO dto
    ) {
        return nommineChangeRequestServices.updateNommineChange(id, dto);
    }

    /** MMC20 View Mode status change — Inactive only, with Inactive rights. */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    @PutMapping("/updatestatus/{id}")
    public NommineChangeRequestDTO updateStatus(
            @PathVariable Integer id,
            @RequestParam("status") ApplicationStatus status
    ) {
        return nommineChangeRequestServices.updateStatus(id, status);
    }

    @DeleteMapping("/deleteNommine/{id}")
    public String deleteNommineChangeRequest(@PathVariable Integer id) {
        return nommineChangeRequestServices.deleteNommineChangeRequestService(id);
    }
}
