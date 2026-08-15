package com.memberconnect.backend.config;

import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class BankDataSeeder implements CommandLineRunner {

    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;

    public BankDataSeeder(BankRepository bankRepository, BranchRepository branchRepository) {
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
    }

    @Override
    public void run(String... args) {
        if (bankRepository.count() > 0) {
            return;
        }

        seedBank(
                "BOC",
                "Bank of Ceylon",
                "Colombo Main",
                "Kandy",
                "Galle"
        );
        seedBank(
                "PB",
                "People's Bank",
                "Colombo",
                "Matara",
                "Jaffna"
        );
        seedBank(
                "HNB",
                "Hatton National Bank",
                "Colombo",
                "Negombo",
                "Kurunegala"
        );

        System.out.println("Seeded default banks and branches.");
    }

    private void seedBank(String bankCode, String bankName, String... branchNames) {
        Bank bank = new Bank(bankName);
        bank.setBankCode(bankCode);
        Bank savedBank = bankRepository.save(bank);

        for (String branchName : branchNames) {
            branchRepository.save(new Branch(branchName, savedBank));
        }
    }
}
