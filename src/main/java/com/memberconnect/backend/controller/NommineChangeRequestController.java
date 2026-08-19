package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.NommineChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.NommineChangeRequestServices;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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
@RestController
@RequestMapping("/api/v3")
@CrossOrigin(origins = "http://localhost:3000")
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

    @PostMapping("/saveNommine")
    public NommineChangeRequestDTO saveNommineChangeRequest(@Valid @RequestBody NommineChangeRequestDTO dto) {
        return nommineChangeRequestServices.NommineChangeRequestaddService(dto);
    }

    /**
     * The entry screen used to post its edits to /saveNommine with an id in the body,
     * leaving this endpoint unused. It is the update path.
     */
    @PutMapping("/updateNommine/{id}")
    public NommineChangeRequestDTO updateNommineChangeRequest(
            @PathVariable Integer id,
            @Valid @RequestBody NommineChangeRequestDTO dto
    ) {
        return nommineChangeRequestServices.updateNommineChange(id, dto);
    }

    /** MMC20 View Mode status change — Inactive only, with Inactive rights. */
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
