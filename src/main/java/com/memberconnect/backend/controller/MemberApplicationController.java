package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberApplicationDTO;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.service.MemberApplicationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@CrossOrigin
public class MemberApplicationController {

    @Autowired
    private MemberApplicationService service;

    @PostMapping("/create")
    public MemberApplicationDTO createMemberApplication(@RequestBody MemberApplicationDTO memberApplicationDTO) {
        return service.saveMemberApplication(memberApplicationDTO);
    }
}