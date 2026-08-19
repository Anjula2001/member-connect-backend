package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.RemittanceMasterAccountDTO;
import com.memberconnect.backend.service.RemittanceMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/remittance-master")
@CrossOrigin
public class RemittanceMasterController {

    @Autowired
    private RemittanceMasterService service;

    // The registration form needs to read the master to render/validate its amount
    // fields, so reading is open to everyone who works with registrations.
    @PreAuthorize("hasAnyRole('DISTRICT_OFFICE','HEAD_OFFICE','BOARD_SECRETARY','ACCOUNTS','SUPER_ADMIN')")
    @GetMapping("/active")
    public List<RemittanceMasterAccountDTO> getActive() {
        return service.getActive();
    }

    // Maintaining the master is an Accounts responsibility — these are contribution
    // amounts, not an IT/admin setting. Super Admin retains an override.
    @PreAuthorize("hasAnyRole('ACCOUNTS','SUPER_ADMIN')")
    @GetMapping
    public List<RemittanceMasterAccountDTO> getAll() {
        return service.getAll();
    }

    @PreAuthorize("hasAnyRole('ACCOUNTS','SUPER_ADMIN')")
    @PutMapping("/{id}")
    public RemittanceMasterAccountDTO update(
            @PathVariable Long id,
            @RequestBody RemittanceMasterAccountDTO dto) {
        return service.update(id, dto);
    }
}
