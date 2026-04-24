package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.service.MinorSavingsAccountService;

@RestController
@RequestMapping("/api/members")
@CrossOrigin(origins = "http://localhost:3000")
public class MinorSavingsAccountController {

    private final MinorSavingsAccountService service;

    public MinorSavingsAccountController(MinorSavingsAccountService service) {
        this.service = service;
    }

    @GetMapping("/{memberId}/minor-savings-accounts")
    public List<MinorSavingsAccount> getMinorSavingsAccounts(
            @PathVariable String memberId
    ) {
        return service.getMinorSavingsAccountsByMemberId(memberId);
    }
}
