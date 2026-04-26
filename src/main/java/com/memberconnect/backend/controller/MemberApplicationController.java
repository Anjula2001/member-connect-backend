package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.service.MemberApplicationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin
public class MemberApplicationController {

    @Autowired
    private MemberApplicationService service;

    @PostMapping("/createApplication")
    public MemberApplicationDTO createMemberApplication(@RequestBody MemberApplicationDTO memberApplicationDTO) {
        return service.saveMemberApplication(memberApplicationDTO);
    }
    @GetMapping("/getApplication")
    public List<MemberApplicationDTO> getUser(){
        return service.getAllMemberApplications();
    }
    @PutMapping("/updateApplication/{id}")
    public MemberApplicationDTO updateMemberApplication(
            @PathVariable Long id,
            @RequestBody MemberApplicationDTO memberApplicationDTO) {

        return service.updateMemberApplication(id, memberApplicationDTO);
    }
    @PatchMapping("/updateApplicationPartial/{id}")
    public MemberApplicationDTO updatePartial(
            @PathVariable Long id,
            @RequestBody MemberApplicationDTO dto) {

        return service.updatePartial(id, dto);
    }
    @DeleteMapping("/deleteApplication/{id}")
    public String deleteMemberApplication(@PathVariable Long id) {
        return service.deleteMemberApplication(id);
    }
    @GetMapping("/{id}")
    public MemberApplicationDTO getApplicationById(@PathVariable Long id) {
        return service.getApplicationById(id);
    }
    @GetMapping("/nic/{nic}")
    public MemberApplicationDTO getApplicationByNic(@PathVariable String nic) {
        return service.getApplicationByNic(nic);
    }
    @PatchMapping("/{id}/status")
    public MemberApplicationDTO updateStatus(
            @PathVariable Long id,
            @RequestParam ApplicationStatus status) {
        return service.updateStatus(id, status);
    }

}