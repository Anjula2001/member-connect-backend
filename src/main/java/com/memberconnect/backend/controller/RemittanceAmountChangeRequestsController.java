package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.ProfileChangeDecisionDTO;
import com.memberconnect.backend.dto.RemittanceAmountChangeDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.RemitanceAmountChangeservices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Remittance Amount Change Requests (Requirement 02, MMC14-MMC17).
 *
 * Authorization follows the Basic Profile pattern, because this is the other type that
 * is decided directly with no board step: District Office raises a request but does not
 * decide it; Super Admin, Head Office and Board Secretary decide; the three specialist
 * roles are kept out of the module entirely.
 *
 * saveRemitance used to return a bare "success" String, so the screen never learned the
 * request number it had just created. It returns the saved record now, as the other
 * three types do.
 */
@RestController
@RequestMapping("/api4/remitance")
@CrossOrigin("http://localhost:3000")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE')")
public class RemittanceAmountChangeRequestsController {

    @Autowired
    public RemitanceAmountChangeservices remittanceAmountChangeservices;

    @GetMapping("/getRemitance")
    public List<RemittanceAmountChangeDTO> getRemitance() {
        return remittanceAmountChangeservices.getRemitanceRequests();
    }

    @GetMapping("/getRemitanceById/{id}")
    public RemittanceAmountChangeDTO getRemittanceById(@PathVariable Integer id) {
        RemittanceAmountChangeDTO dto = remittanceAmountChangeservices.remitanceRequestgetBhyID(id);
        if (dto == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Remittance change request not found: " + id);
        }
        return dto;
    }

    /**
     * MMC14's starting state for a new request: the member's editable accounts, with
     * the current amount in both the Current Value and New Value columns.
     */
    @GetMapping("/new/{memberId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
    public RemittanceAmountChangeDTO newRequest(@PathVariable String memberId) {
        return remittanceAmountChangeservices.newRequestFor(memberId);
    }

    /** MMC14: raised by District Office. Board Secretary decides but never opens one. */
    @PostMapping("/saveRemitance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','DISTRICT_OFFICE')")
    public RemittanceAmountChangeDTO saveRemitance(
            @jakarta.validation.Valid @RequestBody RemittanceAmountChangeDTO dto) {
        return remittanceAmountChangeservices.saveRemittanceRequest(dto);
    }

    /**
     * MMC14 forbids editing a submitted record; in-place editing is enabled at the
     * client's direction and restricted to the roles that can decide the request.
     */
    @PutMapping("/updateRemitance/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    public RemittanceAmountChangeDTO updateRemitance(
            @PathVariable Integer id,
            @jakarta.validation.Valid @RequestBody RemittanceAmountChangeDTO dto) {
        return remittanceAmountChangeservices.updateRemittanceRequest(id, dto);
    }

    /**
     * MMC17 - approve or reject, in one transaction that also writes the member's
     * remittance amounts.
     */
    @PutMapping("/requests/{id}/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    public RemittanceAmountChangeDTO decide(
            @PathVariable Integer id,
            @RequestBody ProfileChangeDecisionDTO decision) {
        return remittanceAmountChangeservices.decide(id, decision);
    }

    /** MMC16 View Mode status change - Inactive only, with Inactive rights. */
    @PutMapping("/updatestatus/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HEAD_OFFICE','BOARD_SECRETARY')")
    public RemittanceAmountChangeDTO updateStatus(
            @PathVariable Integer id,
            @RequestParam("status") ApplicationStatus status) {
        return remittanceAmountChangeservices.updateStatus(id, status);
    }

    @DeleteMapping("/deleteRemitance/{id}")
    public String deleteRemitance(@PathVariable Integer id) {
        return remittanceAmountChangeservices.DeleteRemittanceRequest(id);
    }
}
