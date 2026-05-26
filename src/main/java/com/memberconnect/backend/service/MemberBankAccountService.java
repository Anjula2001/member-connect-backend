package com.memberconnect.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.MemberBankAccountRequestDTO;
import com.memberconnect.backend.dto.MemberBankAccountResponseDTO;
import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.BankBranch;
import com.memberconnect.backend.model.MemberBankAccount;
import com.memberconnect.backend.repository.BankBranchRepository;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.MemberBankAccountRepository;

@Service
public class MemberBankAccountService {

    private final MemberBankAccountRepository memberBankAccountRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;

    public MemberBankAccountService(
            MemberBankAccountRepository memberBankAccountRepository,
            BankRepository bankRepository,
            BankBranchRepository bankBranchRepository
    ) {
        this.memberBankAccountRepository = memberBankAccountRepository;
        this.bankRepository = bankRepository;
        this.bankBranchRepository = bankBranchRepository;
    }

    // Get bank account
    public List<MemberBankAccountResponseDTO> getMemberBankAccounts(String memberId) {
        return memberBankAccountRepository.findByMemberId(memberId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // Save bank account
    public MemberBankAccountResponseDTO saveMemberBankAccount(
            String memberId,
            MemberBankAccountRequestDTO request
    ) {
        List<MemberBankAccount> existingAccounts =
                memberBankAccountRepository.findByMemberId(memberId);

            if (!existingAccounts.isEmpty()) {
                throw new RuntimeException("Only one disbursement bank account is allowed");
            }

        Bank bank = bankRepository.findByBankId(request.getBankId())
                .orElseThrow(() -> new RuntimeException("Invalid bankId"));

        BankBranch branch = bankBranchRepository.findByBranchId(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Invalid branchId"));

        if (!branch.getBankId().equals(bank.getBankId())) {
            throw new RuntimeException("Branch does not belong to bank");
        }

        MemberBankAccount account = new MemberBankAccount();
        account.setMemberId(memberId);
        account.setBankId(request.getBankId());
        account.setBranchId(request.getBranchId());
        account.setAccountNumber(request.getAccountNumber());

        return mapToResponse(memberBankAccountRepository.save(account));
    }

    // UPDATE bank account
    public MemberBankAccountResponseDTO updateMemberBankAccount(
            String memberId,
            Long accountId,
            MemberBankAccountRequestDTO request
    ) {
        MemberBankAccount account = memberBankAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getMemberId().equals(memberId)) {
            throw new RuntimeException("Invalid member access");
        }

        Bank bank = bankRepository.findByBankId(request.getBankId())
                .orElseThrow(() -> new RuntimeException("Invalid bankId"));

        BankBranch branch = bankBranchRepository.findByBranchId(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Invalid branchId"));

        if (!branch.getBankId().equals(bank.getBankId())) {
            throw new RuntimeException("Branch does not belong to bank");
        }

        account.setBankId(request.getBankId());
        account.setBranchId(request.getBranchId());
        account.setAccountNumber(request.getAccountNumber());

        return mapToResponse(memberBankAccountRepository.save(account));
    }


    private MemberBankAccountResponseDTO mapToResponse(MemberBankAccount account) {
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
    }
}