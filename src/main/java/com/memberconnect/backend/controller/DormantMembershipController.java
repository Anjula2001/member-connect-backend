package com.memberconnect.backend.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/dormant-members")
@CrossOrigin(origins = "http://localhost:3000")
public class DormantMembershipController {

    private final DormantMembershipService dormantService;

    public DormantMembershipController(DormantMembershipService dormantService) {
        this.dormantService = dormantService;
    }

    // ---- Configuration ----

    @GetMapping("/config")
    public DormantConfigDTO getConfig() {
        return dormantService.getConfigDto();
    }

    @PutMapping("/config")
    public DormantConfigDTO updateConfig(@RequestBody DormantConfigDTO dto) {
        return dormantService.updateConfig(dto);
    }

    // ---- Identification process ----

    @PostMapping("/run-identification")
    public Map<String, Object> runIdentification() {
        int[] result = dormantService.runIdentification();
        Map<String, Object> response = new HashMap<>();
        response.put("selected", result[0]);
        response.put("cleared", result[1]);
        return response;
    }

    // ---- Filters metadata ----

    @GetMapping("/locations")
    public List<String> getLocations() {
        return dormantService.getLocations();
    }

    @GetMapping("/member-types")
    public List<String> getMemberTypes() {
        return dormantService.getMemberTypes();
    }

    // ---- Search / view ----

    @GetMapping("/search")
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
    public DormantApprovalListDTO createApprovalList(@RequestBody DormantApprovalListDTO dto) {
        return dormantService.createApprovalList(dto);
    }

    @GetMapping("/approval-lists")
    public List<DormantApprovalListDTO> getAllApprovalLists() {
        return dormantService.getAllApprovalLists();
    }

    @GetMapping("/approval-lists/{listId}")
    public DormantApprovalListDTO getApprovalList(@PathVariable String listId) {
        return dormantService.getApprovalList(listId);
    }

    @GetMapping("/approval-lists/{listId}/members")
    public List<DormantMemberDTO> getApprovalListMembers(@PathVariable String listId) {
        return dormantService.getMembersByListId(listId);
    }

    @PatchMapping("/approval-lists/{listId}/process")
    public DormantApprovalListDTO processApprovalList(
            @PathVariable String listId,
            @RequestBody DormantApprovalListDTO dto
    ) {
        return dormantService.processApprovalList(listId, dto);
    }

    @PatchMapping("/approval-lists/{listId}/members/{memberId}/decision")
    public DormantApprovalListDTO decideMember(
            @PathVariable String listId,
            @PathVariable String memberId,
            @RequestBody DormantApprovalListDTO dto
    ) {
        return dormantService.decideMember(listId, memberId, dto.getDecision());
    }

    @PatchMapping("/approval-lists/{listId}/inactivate")
    public DormantApprovalListDTO inactivateMembers(
            @PathVariable String listId,
            @RequestBody(required = false) DormantApprovalListDTO dto
    ) {
        List<String> memberIds = dto != null ? dto.getMemberIds() : null;
        return dormantService.inactivateMembers(listId, memberIds);
    }

    @DeleteMapping("/approval-lists/{listId}")
    public String deleteApprovalList(@PathVariable String listId) {
        return dormantService.deleteApprovalList(listId);
    }
}
