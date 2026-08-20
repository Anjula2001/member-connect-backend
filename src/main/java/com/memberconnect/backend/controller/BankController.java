package com.memberconnect.backend.controller;

import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import org.springframework.data.domain.Sort;
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

    
    // Endpoint to get all banks
    @GetMapping("/banks")
    public List<Bank> getBanks() {
        // Alphabetical: the master now holds every licensed bank, and an
        // insertion-ordered dropdown of that length is unusable.
        return bankRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Endpoint to get branches by bank ID
    @GetMapping("/branches/{bankId}")
    public List<Branch> getBranchesByBank(@PathVariable Long bankId) {
        return branchRepository.findByBankIdOrderByNameAsc(bankId);
    }
}