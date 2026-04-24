package com.memberconnect.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memberconnect.backend.dto.MemberBankAccountRequestDTO;
import com.memberconnect.backend.dto.MemberBankAccountResponseDTO;
import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.BankBranch;
import com.memberconnect.backend.model.MemberBankAccount;
import com.memberconnect.backend.repository.BankBranchRepository;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.MemberBankAccountRepository;

@RestController
@RequestMapping("/api/members/{memberId}/bank-accounts")
@CrossOrigin(origins = "http://localhost:3000")
public class MemberBankAccountController {

    private final MemberBankAccountRepository memberBankAccountRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;

    public MemberBankAccountController(
            MemberBankAccountRepository memberBankAccountRepository,
            BankRepository bankRepository,
            BankBranchRepository bankBranchRepository
    ) {
        this.memberBankAccountRepository = memberBankAccountRepository;
        this.bankRepository = bankRepository;
        this.bankBranchRepository = bankBranchRepository;
    }

    @GetMapping
    public List<MemberBankAccountResponseDTO> getMemberBankAccounts(@PathVariable String memberId) {
        return memberBankAccountRepository.findByMemberId(memberId)
                .stream()
                .map(account -> {
                    Bank bank = bankRepository.findByBankId(account.getBankId()).orElse(null);
                    BankBranch branch = bankBranchRepository.findByBranchId(account.getBranchId()).orElse(null);

                    return new MemberBankAccountResponseDTO(
                            account.getId(),
                            account.getMemberId(),
                            account.getBankId(),
                            bank != null ? bank.getName() : "",
                            account.getBranchId(),
                            branch != null ? branch.getName() : "",
                            account.getAccountNumber()
                    );
                })
                .toList();
    }

    @PostMapping
    public ResponseEntity<MemberBankAccountResponseDTO> saveMemberBankAccount(
            @PathVariable String memberId,
            @RequestBody MemberBankAccountRequestDTO request
    ) {
        Bank bank = bankRepository.findByBankId(request.getBankId())
                .orElseThrow(() -> new RuntimeException("Invalid bankId"));

        BankBranch branch = bankBranchRepository.findByBranchId(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Invalid branchId"));

        if (!branch.getBankId().equals(bank.getBankId())) {
            throw new RuntimeException("Selected branch does not belong to selected bank");
        }

        MemberBankAccount account = new MemberBankAccount();
        account.setMemberId(memberId);
        account.setBankId(request.getBankId());
        account.setBranchId(request.getBranchId());
        account.setAccountNumber(request.getAccountNumber());

        MemberBankAccount saved = memberBankAccountRepository.save(account);

        MemberBankAccountResponseDTO response = new MemberBankAccountResponseDTO(
                saved.getId(),
                saved.getMemberId(),
                saved.getBankId(),
                bank.getName(),
                saved.getBranchId(),
                branch.getName(),
                saved.getAccountNumber()
        );

        return ResponseEntity.ok(response);
    }

}