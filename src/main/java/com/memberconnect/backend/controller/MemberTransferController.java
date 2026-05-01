package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberTransferDto;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.service.MemberTransferService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member-transfers")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberTransferController {

    private final MemberTransferService memberTransferService;

    public MemberTransferController(MemberTransferService memberTransferService) {
        this.memberTransferService = memberTransferService;
    }

    @GetMapping
    public List<MemberTransferRequest> getAllRequests() {
        return memberTransferService.getAllRequests();
    }

    @GetMapping("/{id}")
    public MemberTransferRequest getRequestById(@PathVariable Long id) {
        return memberTransferService.getRequestById(id);
    }


    @PostMapping("/submit")
    public MemberTransferRequest submitRequest(@RequestBody MemberTransferDto dto) {
        return memberTransferService.submitRequest(dto);
    }

}