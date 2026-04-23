package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class BankController {

    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;

    public BankController(BankRepository bankRepository, BranchRepository branchRepository) {
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
    }

    @GetMapping("/banks")
    public List<Bank> getBanks() {
        return bankRepository.findAll();
    }

    @GetMapping("/branches/{bankId}")
    public List<Branch> getBranchesByBank(@PathVariable Long bankId) {
        return branchRepository.findByBankId(bankId);
    }
}