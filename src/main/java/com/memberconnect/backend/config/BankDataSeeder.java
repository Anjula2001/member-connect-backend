package com.memberconnect.backend.config;

import com.memberconnect.backend.model.Bank;
import com.memberconnect.backend.model.Branch;
import com.memberconnect.backend.repository.BankRepository;
import com.memberconnect.backend.repository.BranchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the bank / branch master data that every disbursement picker in the app
 * reads - nominee bank on a death record, minor savings disbursement, member
 * bank accounts, termination and retirement payouts.
 *
 * This used to bail out entirely when the banks table already held a row, so the
 * three banks it originally created were most of what the app ever offered, and
 * adding a bank meant hand-writing SQL against the shared database. It now
 * reconciles instead: every bank below is matched on its bank code (falling back
 * to its name, for rows created before bank_code existed), created when missing,
 * and each of its branches inserted only when that bank does not already have a
 * branch by that name. Nothing here updates or deletes, so banks and branches
 * added by hand in the database survive a boot untouched.
 */
@Component
@Order(1)
public class BankDataSeeder implements CommandLineRunner {

    private final BankRepository bankRepository;
    private final BranchRepository branchRepository;

    public BankDataSeeder(BankRepository bankRepository, BranchRepository branchRepository) {
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
    }

    /** Rows per branch-insert transaction; see the save loop for why. */
    private static final int BRANCH_INSERT_CHUNK = 50;

    /** One bank and the branches it should offer. */
    private record BankSeed(String bankCode, String name, String[] branches) {}

    // The branch sets are shared by reference rather than repeated per bank: the
    // island-wide banks all cover the same district network, so listing it once
    // keeps the tiers consistent and the file readable.

    /** District network offered by the large state and private retail banks. */
    private static final String[] NATIONWIDE = {
            "Colombo Fort", "Pettah", "Kollupitiya", "Bambalapitiya", "Wellawatte",
            "Borella", "Maradana", "Nugegoda", "Dehiwala", "Mount Lavinia",
            "Moratuwa", "Maharagama", "Battaramulla", "Kelaniya", "Gampaha",
            "Negombo", "Ja-Ela", "Kalutara", "Panadura", "Horana",
            "Kandy", "Matale", "Nuwara Eliya", "Galle", "Matara",
            "Hambantota", "Jaffna", "Vavuniya", "Trincomalee", "Batticaloa",
            "Ampara", "Kalmunai", "Kurunegala", "Chilaw", "Puttalam",
            "Anuradhapura", "Polonnaruwa", "Badulla", "Bandarawela", "Monaragala",
            "Ratnapura", "Embilipitiya", "Kegalle"
    };

    /** Smaller private and development banks: provincial centres only. */
    private static final String[] REGIONAL = {
            "Colombo Fort", "Kollupitiya", "Nugegoda", "Negombo", "Gampaha",
            "Kalutara", "Kandy", "Nuwara Eliya", "Galle", "Matara",
            "Jaffna", "Trincomalee", "Batticaloa", "Kurunegala", "Anuradhapura",
            "Badulla", "Ratnapura"
    };

    /** Single-office specialised lenders. */
    private static final String[] METRO = {
            "Head Office - Colombo 02", "Kandy", "Galle", "Kurunegala", "Anuradhapura"
    };

    /** Foreign banks, which in Sri Lanka run only a handful of city branches. */
    private static final String[] FOREIGN = {
            "Colombo 01 - Main", "Colombo 03", "Kandy"
    };

    private static final List<BankSeed> BANKS = List.of(
            // Licensed commercial banks - state and large private
            new BankSeed("BOC", "Bank of Ceylon", NATIONWIDE),
            new BankSeed("PB", "People's Bank", NATIONWIDE),
            new BankSeed("COMB", "Commercial Bank of Ceylon", NATIONWIDE),
            new BankSeed("HNB", "Hatton National Bank", NATIONWIDE),
            new BankSeed("SAMP", "Sampath Bank", NATIONWIDE),
            new BankSeed("SEYL", "Seylan Bank", NATIONWIDE),
            new BankSeed("NSB", "National Savings Bank", NATIONWIDE),

            // Licensed commercial banks - mid sized private
            new BankSeed("NTB", "Nations Trust Bank", REGIONAL),
            new BankSeed("DFCC", "DFCC Bank", REGIONAL),
            new BankSeed("NDB", "National Development Bank", REGIONAL),
            new BankSeed("PABC", "Pan Asia Banking Corporation", REGIONAL),
            new BankSeed("UBC", "Union Bank of Colombo", REGIONAL),
            new BankSeed("AMANA", "Amana Bank", REGIONAL),
            new BankSeed("CARG", "Cargills Bank", REGIONAL),

            // Licensed specialised banks
            new BankSeed("SDB", "SANASA Development Bank", REGIONAL),
            new BankSeed("RDB", "Regional Development Bank", REGIONAL),
            new BankSeed("HDFC", "HDFC Bank of Sri Lanka", REGIONAL),
            new BankSeed("SMIB", "State Mortgage and Investment Bank", METRO),
            new BankSeed("LDB", "Lankaputhra Development Bank", METRO),
            new BankSeed("SLSB", "Sri Lanka Savings Bank", METRO),

            // Foreign banks operating locally
            new BankSeed("SCB", "Standard Chartered Bank", FOREIGN),
            new BankSeed("HSBC", "The Hongkong and Shanghai Banking Corporation", FOREIGN),
            new BankSeed("CITI", "Citibank N.A.", FOREIGN),
            new BankSeed("DEUT", "Deutsche Bank AG", FOREIGN),
            new BankSeed("ICICI", "ICICI Bank", FOREIGN),
            new BankSeed("IOB", "Indian Overseas Bank", FOREIGN),
            new BankSeed("INDB", "Indian Bank", FOREIGN),
            new BankSeed("SBI", "State Bank of India", FOREIGN),
            new BankSeed("MCB", "MCB Bank", FOREIGN),
            new BankSeed("HBL", "Habib Bank", FOREIGN),
            new BankSeed("PBB", "Public Bank Berhad", FOREIGN)
    );

    @Override
    public void run(String... args) {
        // The database is remote, so both sides are read once up front rather
        // than a lookup per bank and per branch.
        Map<String, Bank> byCode = new HashMap<>();
        Map<String, Bank> byName = new HashMap<>();
        for (Bank bank : bankRepository.findAll()) {
            if (bank.getBankCode() != null) {
                byCode.put(key(bank.getBankCode()), bank);
            }
            byName.put(key(bank.getName()), bank);
        }

        Map<Long, Set<String>> branchNamesByBank = new HashMap<>();
        for (Branch branch : branchRepository.findAll()) {
            branchNamesByBank
                    .computeIfAbsent(branch.getBank().getId(), id -> new HashSet<>())
                    .add(key(branch.getName()));
        }

        int newBanks = 0;
        List<Branch> newBranches = new ArrayList<>();

        for (BankSeed seed : BANKS) {
            Bank bank = byCode.get(key(seed.bankCode()));
            if (bank == null) {
                // Rows seeded before bank_code existed are matched on name so we
                // do not trip the unique constraint with a duplicate.
                bank = byName.get(key(seed.name()));
            }

            if (bank == null) {
                bank = new Bank(seed.name());
                bank.setBankCode(seed.bankCode());
                bank = bankRepository.save(bank);
                newBanks++;
            } else if (bank.getBankCode() == null) {
                bank.setBankCode(seed.bankCode());
                bank = bankRepository.save(bank);
            }

            Set<String> existing =
                    branchNamesByBank.computeIfAbsent(bank.getId(), id -> new HashSet<>());
            for (String branchName : seed.branches()) {
                if (existing.add(key(branchName))) {
                    newBranches.add(new Branch(branchName, bank));
                }
            }
        }

        // Branch ids are IDENTITY, so Hibernate cannot JDBC-batch these inserts:
        // each row is its own remote round trip. Saving all of them in one call
        // held a pooled connection past Hikari's leak-detection threshold on the
        // first run, so the work is committed in chunks instead.
        for (int from = 0; from < newBranches.size(); from += BRANCH_INSERT_CHUNK) {
            int to = Math.min(from + BRANCH_INSERT_CHUNK, newBranches.size());
            branchRepository.saveAll(newBranches.subList(from, to));
        }

        if (newBanks > 0 || !newBranches.isEmpty()) {
            System.out.printf(
                    "Bank master data reconciled: %d new bank(s), %d new branch(es).%n",
                    newBanks, newBranches.size());
        }
    }

    /** Case and whitespace insensitive key, so "Kandy " never duplicates "Kandy". */
    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
