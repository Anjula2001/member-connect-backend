package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.UniversityMasterDto;
import com.memberconnect.backend.service.UniversityMasterService;

/**
 * University Scholarship master data maintenance.
 *
 * Super Admin only, matching UserAdminController. The existing US_MASTER_MANAGE
 * permission would also have fitted, but it is held by SCHOLARSHIP_OFFICER as well and
 * this screen was specified as Super Admin only. The read endpoints the Scholarship
 * form uses (/api/universities, /api/programs/{id}, /api/duration) are untouched and
 * still open to US_MASTER_VIEW, so restricting this screen does not affect them.
 *
 * No delete endpoint: these rows are referenced by existing scholarship requests.
 */
@RestController
@RequestMapping("/api/admin/university-master")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UniversityMasterController {

    private final UniversityMasterService service;

    public UniversityMasterController(UniversityMasterService service) {
        this.service = service;
    }

    // ---- Universities -------------------------------------------------------

    @GetMapping("/universities")
    public List<UniversityMasterDto> getUniversities() {
        return service.getUniversities();
    }

    @PostMapping("/universities")
    public UniversityMasterDto createUniversity(@RequestBody UniversityMasterDto request) {
        return service.createUniversity(request);
    }

    @PutMapping("/universities/{id}")
    public UniversityMasterDto updateUniversity(@PathVariable Long id,
                                                @RequestBody UniversityMasterDto request) {
        return service.updateUniversity(id, request);
    }

    // ---- Programmes ---------------------------------------------------------

    @GetMapping("/programs")
    public List<UniversityMasterDto> getPrograms() {
        return service.getPrograms();
    }

    @PostMapping("/programs")
    public UniversityMasterDto createProgram(@RequestBody UniversityMasterDto request) {
        return service.createProgram(request);
    }

    @PutMapping("/programs/{id}")
    public UniversityMasterDto updateProgram(@PathVariable Long id,
                                             @RequestBody UniversityMasterDto request) {
        return service.updateProgram(id, request);
    }

    // ---- University / Programme pairings ------------------------------------

    @GetMapping("/university-programs")
    public List<UniversityMasterDto> getUniversityPrograms() {
        return service.getUniversityPrograms();
    }

    @PostMapping("/university-programs")
    public UniversityMasterDto createUniversityProgram(@RequestBody UniversityMasterDto request) {
        return service.createUniversityProgram(request);
    }

    @PutMapping("/university-programs/{id}")
    public UniversityMasterDto updateUniversityProgram(@PathVariable Long id,
                                                       @RequestBody UniversityMasterDto request) {
        return service.updateUniversityProgram(id, request);
    }
}
