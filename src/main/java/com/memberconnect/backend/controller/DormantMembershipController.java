package com.memberconnect.backend.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.DormantApprovalListDTO;
import com.memberconnect.backend.dto.DormantConfigDTO;
import com.memberconnect.backend.dto.DormantMemberDTO;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * Inactivating Dormant Membership Profiles (SRS Requirement 05, section 4).
 *
 * The SRS names the Head Office System User as the actor for MMD12-MMD18 and an
 * "Authorized Head Office System User" for the manual identification run. Two
 * finer distinctions in the text are honoured here: MMD15 calls deletion a
 * privilege separate from ordinary Head Office access, and 4.2.3 says the
 * Location filter is un-editable for a user who only has access to their own
 * district - which is what admits the District Office to the view functions,
 * read-only and scoped by the server rather than by the dropdown.
 *
 * Deliberately absent: DISTRICT_COMMITTEE and PD_COMMITTEE are the second and
 * third approval levels for Member Death and Death Donations only - dormancy is
 * decided by the Board, not by a committee ladder. ACCOUNTS owns the Remittance
 * Master and the Finance edge, not the membership decision. SCHOLARSHIP_OFFICER
 * and DEATH_DONATION_OFFICER are actors in neither.
 *
 * Annotations are per-method rather than class-level plus overrides: READ_ROLES
 * is a superset of BOARD_ROLES here, so there is no one sensible default, and
 * DormantEndpointSecurityTest pins one exact expression per handler.
 */
@RestController
@RequestMapping("/api/dormant-members")
@CrossOrigin(origins = "http://localhost:3000")
public class DormantMembershipController {

    /** MMD12: everyone who reads the dormant population. District Office is
        read-only and pinned by the service to their own district. */
    static final String READ_ROLES =
            "hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','DISTRICT_OFFICE','SUPER_ADMIN')";

    /**
     * The dormancy period decides who gets inactivated, which makes it
     * membership policy rather than an operational setting - so it follows
     * ELIGIBILITY_CONFIG_ROLES and stays with Super Admin. Head Office is the
     * defensible alternative and is flagged for client sign-off.
     */
    static final String CONFIG_WRITE_ROLES = "hasRole('SUPER_ADMIN')";

    /** MMD11: running the identification process off-schedule. */
    static final String IDENTIFICATION_ROLES = "hasAnyRole('HEAD_OFFICE','SUPER_ADMIN')";

    /** MMD13/14/16/17/18: the board half. Same list, and the same reason, as
        TerminationApprovalListController. */
    static final String BOARD_ROLES = "hasAnyRole('HEAD_OFFICE','BOARD_SECRETARY','SUPER_ADMIN')";

    /** MMD15: "delete privileges" is narrower than Head Office access, exactly
        as DELETE_RIGHTS_ROLES already draws it elsewhere. */
    static final String DELETE_ROLES = "hasAnyRole('BOARD_SECRETARY','SUPER_ADMIN')";

    private final DormantMembershipService dormantService;

    public DormantMembershipController(DormantMembershipService dormantService) {
        this.dormantService = dormantService;
    }

    // ---- Configuration ----

    @GetMapping("/config")
    @PreAuthorize(READ_ROLES)
    public DormantConfigDTO getConfig() {
        return dormantService.getConfigDto();
    }

    @PutMapping("/config")
    @PreAuthorize(CONFIG_WRITE_ROLES)
    public DormantConfigDTO updateConfig(@RequestBody DormantConfigDTO dto) {
        return dormantService.updateConfig(dto);
    }

    // ---- Identification process ----

    @PostMapping("/run-identification")
    @PreAuthorize(IDENTIFICATION_ROLES)
    public Map<String, Object> runIdentification() {
        int[] result = dormantService.runIdentification();
        Map<String, Object> response = new HashMap<>();
        response.put("selected", result[0]);
        response.put("cleared", result[1]);
        return response;
    }

    // ---- Filters metadata ----

    @GetMapping("/locations")
    @PreAuthorize(READ_ROLES)
    public List<String> getLocations() {
        return dormantService.getLocations();
    }

    @GetMapping("/member-types")
    @PreAuthorize(READ_ROLES)
    public List<String> getMemberTypes() {
        return dormantService.getMemberTypes();
    }

    // ---- Search / view ----

    @GetMapping("/search")
    @PreAuthorize(READ_ROLES)
    public List<DormantMemberDTO> search(
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) String memberType,
            @RequestParam(required = false, defaultValue = "all") String dateFilter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "lastActivity") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        return dormantService.searchDormantMembers(
                locations, memberType, dateFilter, fromDate, toDate, statuses, search, sortBy, sortOrder);
    }

    // ---- Approval lists (Board integration) ----

    @PostMapping("/approval-lists")
    @PreAuthorize(BOARD_ROLES)
    public DormantApprovalListDTO createApprovalList(@RequestBody DormantApprovalListDTO dto) {
        return dormantService.createApprovalList(dto);
    }

    @GetMapping("/approval-lists")
    @PreAuthorize(BOARD_ROLES)
    public List<DormantApprovalListDTO> getAllApprovalLists() {
        return dormantService.getAllApprovalLists();
    }

    @GetMapping("/approval-lists/{listId}")
    @PreAuthorize(BOARD_ROLES)
    public DormantApprovalListDTO getApprovalList(@PathVariable String listId) {
        return dormantService.getApprovalList(listId);
    }

    @GetMapping("/approval-lists/{listId}/members")
    @PreAuthorize(BOARD_ROLES)
    public List<DormantMemberDTO> getApprovalListMembers(@PathVariable String listId) {
        return dormantService.getMembersByListId(listId);
    }

    @PatchMapping("/approval-lists/{listId}/process")
    @PreAuthorize(BOARD_ROLES)
    public DormantApprovalListDTO processApprovalList(
            @PathVariable String listId,
            @RequestBody DormantApprovalListDTO dto
    ) {
        return dormantService.processApprovalList(listId, dto);
    }

    @DeleteMapping("/approval-lists/{listId}")
    @PreAuthorize(DELETE_ROLES)
    public String deleteApprovalList(@PathVariable String listId) {
        return dormantService.deleteApprovalList(listId);
    }
}
