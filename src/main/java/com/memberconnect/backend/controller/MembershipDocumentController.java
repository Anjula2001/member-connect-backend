package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.DispatchRequestDTO;
import com.memberconnect.backend.dto.MemberDTO;
import com.memberconnect.backend.dto.MemberDocumentDispatchDTO;
import com.memberconnect.backend.dto.PrintRequestDTO;
import com.memberconnect.backend.enums.MembershipDocumentType;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.service.MembershipDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/membership-documents")
@CrossOrigin
public class MembershipDocumentController {

    @Autowired
    private MembershipDocumentService service;

    /**
     * Marks a document as printed. Printing membership documentation is a Head
     * Office function — District Office does not issue cards or passbooks.
     */
    @PreAuthorize("hasAnyRole('HEAD_OFFICE','SUPER_ADMIN')")
    @PostMapping("/{type}/print")
    public List<MemberDTO> markPrinted(
            @PathVariable MembershipDocumentType type,
            @RequestBody PrintRequestDTO request) {
        return service.markPrinted(
                type,
                request.getMemberIds(),
                Boolean.TRUE.equals(request.getReprint()));
    }

    /** Dispatch is done by whoever physically posts the documents. */
    @PreAuthorize("hasAnyRole('HEAD_OFFICE','DISTRICT_OFFICE','SUPER_ADMIN')")
    @GetMapping("/dispatch/candidates")
    public List<MemberDTO> getDispatchCandidates(
            @RequestParam(defaultValue = "true") boolean onlyNonDispatched) {
        return service.getDispatchCandidates(onlyNonDispatched);
    }

    @PreAuthorize("hasAnyRole('HEAD_OFFICE','DISTRICT_OFFICE','SUPER_ADMIN')")
    @PostMapping("/dispatch")
    public MemberDocumentDispatchDTO createDispatch(@RequestBody DispatchRequestDTO request) {
        return service.createDispatch(request.getMemberIds(), currentUsername());
    }

    @PreAuthorize("hasAnyRole('HEAD_OFFICE','DISTRICT_OFFICE','SUPER_ADMIN')")
    @GetMapping("/dispatch")
    public List<MemberDocumentDispatchDTO> getDispatches() {
        return service.getDispatches();
    }

    @PreAuthorize("hasAnyRole('HEAD_OFFICE','DISTRICT_OFFICE','SUPER_ADMIN')")
    @GetMapping("/dispatch/{dispatchNo}")
    public MemberDocumentDispatchDTO getDispatch(@PathVariable String dispatchNo) {
        return service.getDispatch(dispatchNo);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getFullName() != null ? user.getFullName() : user.getUsername();
        }
        return "System";
    }
}
