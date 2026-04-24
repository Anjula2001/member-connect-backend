package com.memberconnect.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;

@Service
public class MinorSavingsAccountService {

    private final MinorSavingsAccountRepository minorSavingsAccountRepository;

    public MinorSavingsAccountService(MinorSavingsAccountRepository minorSavingsAccountRepository) {
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
    }

    public List<MinorSavingsAccount> getMinorSavingsAccountsByMemberId(String memberId) {
        return minorSavingsAccountRepository.findByMemberId(memberId);
    }
}