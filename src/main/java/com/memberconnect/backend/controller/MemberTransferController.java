package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.MemberTransferDto;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.service.MemberTransferService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member-transfer")
@CrossOrigin(origins = "*")
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

    @PostMapping("/save")
    public MemberTransferRequest saveRequest(@RequestBody MemberTransferDto dto) {
        return memberTransferService.saveRequest(dto);
    }

    @PutMapping("/{id}")
    public MemberTransferRequest updateRequest(
            @PathVariable Long id,
            @RequestBody MemberTransferDto dto
    ) {
        return memberTransferService.updateRequest(id, dto);
    }

    @PostMapping("/submit")
    public MemberTransferRequest submitRequest(@RequestBody MemberTransferDto dto) {
        return memberTransferService.submitRequest(dto);
    }

    @DeleteMapping("/{id}")
    public String deleteRequest(@PathVariable Long id) {
        memberTransferService.deleteRequest(id);
        return "Member transfer request deleted successfully";
    }
}