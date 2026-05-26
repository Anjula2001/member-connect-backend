package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.BankBranchDTO;
import com.memberconnect.backend.dto.BankDTO;
import com.memberconnect.backend.repository.BankBranchRepository;
import com.memberconnect.backend.repository.BankRepository;

@RestController
@RequestMapping("/api/banks")
@CrossOrigin(origins = "http://localhost:3000")
public class BankController {

    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;

    public BankController(BankRepository bankRepository, BankBranchRepository bankBranchRepository) {
        this.bankRepository = bankRepository;
        this.bankBranchRepository = bankBranchRepository;
    }

    // Get all banks
    @GetMapping
    public List<BankDTO> getBanks() {
        return bankRepository.findAll()
                .stream()
                .map(bank -> new BankDTO(bank.getBankId(), bank.getName()))
                .toList();
    }

    // Get branches for a specific bank
    @GetMapping("/{bankId}/branches")
    public List<BankBranchDTO> getBranchesByBank(@PathVariable String bankId) {
        return bankBranchRepository.findByBankId(bankId)
                .stream()
                .map(branch -> new BankBranchDTO(branch.getBranchId(), branch.getName()))
                .toList();
    }

}