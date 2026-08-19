package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.NameChangeRequestDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.NameChangeRequstServices;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Name Change Requests (Requirement 02, MMC05-MMC07).
 *
 * The approval-list side of the flow (MMC08-MMC13) lives with the board approval
 * lists, not here.
 */
@RestController
@RequestMapping("/api5/namechange")
@CrossOrigin(origins = "http://localhost:3000")
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
    @PostMapping("/savenamechange")
    public NameChangeRequestDTO saveNameChangeRequest(@Valid @RequestBody NameChangeRequestDTO dto) {
        return nameChangeRequstServices.addNameChangeRequestService(dto);
    }

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
