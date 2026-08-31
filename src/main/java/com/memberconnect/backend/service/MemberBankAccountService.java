package com.memberconnect.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.dto.MemberBankAccountRequestDTO;
import com.memberconnect.backend.dto.MemberBankAccountResponseDTO;
import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.model.MemberBankAccount;
import com.memberconnect.backend.repository.BranchRepository;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.MemberBankAccountRepository;

@Service
public class MemberBankAccountService {

    private final MemberBankAccountRepository memberBankAccountRepository;
    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;

    public MemberBankAccountService(
            MemberBankAccountRepository memberBankAccountRepository,
            BankRepository bankRepository,
            BranchRepository branchRepository
    ) {
        this.memberBankAccountRepository = memberBankAccountRepository;
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
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

        Bank bank;
        try {
            bank = bankRepository.findById(Long.parseLong(request.getBankId()))
                    .orElseThrow(() -> new RuntimeException("Invalid bank"));
        } catch (NumberFormatException e) {
            bank = bankRepository.findByBankCode(request.getBankId())
                    .orElseThrow(() -> new RuntimeException("Invalid bankCode"));
        }

        Branch branch = branchRepository.findById(Long.parseLong(request.getBranchId()))
                .orElseThrow(() -> new RuntimeException("Invalid branchId"));

        if (!branch.getBank().getId().equals(bank.getId())) {
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

        Bank bank;
        try {
            bank = bankRepository.findById(Long.parseLong(request.getBankId()))
                    .orElseThrow(() -> new RuntimeException("Invalid bank"));
        } catch (NumberFormatException e) {
            bank = bankRepository.findByBankCode(request.getBankId())
                    .orElseThrow(() -> new RuntimeException("Invalid bankCode"));
        }

        Branch branch = branchRepository.findById(Long.parseLong(request.getBranchId()))
                .orElseThrow(() -> new RuntimeException("Invalid branchId"));

        if (!branch.getBank().getId().equals(bank.getId())) {
            throw new RuntimeException("Branch does not belong to bank");
        }

        account.setBankId(request.getBankId());
        account.setBranchId(request.getBranchId());
        account.setAccountNumber(request.getAccountNumber());

        return mapToResponse(memberBankAccountRepository.save(account));
    }


    private MemberBankAccountResponseDTO mapToResponse(MemberBankAccount account) {
        Bank bank = null;
        try {
            bank = bankRepository.findById(Long.parseLong(account.getBankId())).orElse(null);
        } catch (NumberFormatException e) {
            bank = bankRepository.findByBankCode(account.getBankId()).orElse(null);
        }

        Branch branch = null;
        try {
            branch = branchRepository.findById(Long.parseLong(account.getBranchId())).orElse(null);
        } catch (NumberFormatException e) {
            // fallback
        }

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