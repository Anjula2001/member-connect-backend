package com.memberconnect.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.MemberBankAccountRequestDTO;
import com.memberconnect.backend.dto.MemberBankAccountResponseDTO;
import com.memberconnect.backend.service.MemberBankAccountService;

@RestController
@RequestMapping("/api/members/{memberId}/bank-accounts")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberBankAccountController {

    private final MemberBankAccountService service;

    public MemberBankAccountController(MemberBankAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberBankAccountResponseDTO> getMemberBankAccounts(
            @PathVariable String memberId
    ) {
        return service.getMemberBankAccounts(memberId);
    }

    @PostMapping
    public ResponseEntity<?> saveMemberBankAccount(
            @PathVariable String memberId,
            @RequestBody MemberBankAccountRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(
                    service.saveMemberBankAccount(memberId, request)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<?> updateMemberBankAccount(
            @PathVariable String memberId,
            @PathVariable Long accountId,
            @RequestBody MemberBankAccountRequestDTO request
    ) {
        try {
            return ResponseEntity.ok(
                    service.updateMemberBankAccount(memberId, accountId, request)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }
}