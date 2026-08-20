package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MinorSavingsAccount;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.repository.MinorSavingsAccountRepository;

/**
 * Gives Active members a few minor savings accounts so the "Minor Saving Disbursement"
 * panel has something to show.
 *
 * <h2>Why this is needed</h2>
 *
 * The minor_savings_account table is empty on the demo database, so MMT01's
 * disbursement section renders as an empty table on every member - which looks like a
 * broken screen but is the feature correctly showing that there is nothing to disburse.
 * Without rows here the section cannot be demonstrated or tested at all.
 *
 * <h2>What seeding this actually turns on</h2>
 *
 * More than the one panel. DocumentService.getRequiredDocuments() appends
 * TERMINATION_MINOR / RETIREMENT_MINOR the moment a member has any row in this table,
 * and TerminationDocumentSeeder marks "Minor Savings Account Disbursement Instruction"
 * mandatory - so from here on every seeded member needs that upload, plus complete
 * disbursement bank details on each account, before a termination or retirement request
 * for them can be submitted. That is the documented behaviour rather than a side
 * effect, but it is the reason this is a deliberate one-off switch and not a seeder
 * that runs on every boot.
 *
 * <h2>Scope</h2>
 *
 * Every member with status ACTIVE, by instruction. Note this reaches genuine
 * membership records and not only MEM-DEMO- ones, which is a deliberate departure from
 * DormantDemoCandidateSeeder's demo-prefix guard. What keeps it undoable is the account
 * number: every row created here is MSA-DEMO-*, so undo-demo-seed.sql can remove
 * exactly these rows - including the ones written against a real member, which a
 * member-id filter would strand.
 *
 * <h2>Off by default</h2>
 *
 * Enable with {@code minor.savings.seed.demo=true} (or MINOR_SAVINGS_SEED_DEMO=true)
 * for a single run, then turn it off. Re-running is harmless regardless: a member that
 * already has accounts is skipped untouched, so this can never duplicate a row or
 * overwrite a balance someone has since edited.
 */
@Component
@Order(11)
public class MinorSavingsDemoSeeder implements CommandLineRunner {

    /** Rows created here are identifiable - and removable - by this prefix. */
    private static final String ACCOUNT_PREFIX = "MSA-DEMO-";

    /** Stripped from the member id when building an account number. */
    private static final String MEMBER_PREFIX = "MEM-";

    /**
     * Holder names, cycled through by index. A minor savings account is held for a
     * child, so these are deliberately not the member's own name.
     */
    private static final List<String> HOLDER_NAMES = List.of(
            "Nimasha Perera",
            "Sahan Fernando",
            "Dilini Jayawardena",
            "Kavindu Bandara",
            "Thisari Wickramasinghe",
            "Ravindu Gunasekara"
    );

    private static final int MIN_BALANCE = 15_000;
    private static final int MAX_BALANCE = 250_000;

    private final MemberRepository memberRepository;
    private final MinorSavingsAccountRepository minorSavingsAccountRepository;
    private final boolean enabled;

    public MinorSavingsDemoSeeder(
            MemberRepository memberRepository,
            MinorSavingsAccountRepository minorSavingsAccountRepository,
            @Value("${minor.savings.seed.demo:false}") boolean enabled
    ) {
        this.memberRepository = memberRepository;
        this.minorSavingsAccountRepository = minorSavingsAccountRepository;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        // Says so either way, like the dormant seeder: one that goes quiet when
        // switched off is indistinguishable from one that never ran.
        System.out.println("Minor savings demo seeder: enabled=" + enabled);

        if (!enabled) {
            return;
        }

        List<Member> members = memberRepository.findByStatus(MemberStatus.ACTIVE).stream()
                .filter(m -> m.getMemberId() != null && !m.getMemberId().isBlank())
                .sorted((a, b) -> a.getMemberId().compareTo(b.getMemberId()))
                .toList();

        if (members.isEmpty()) {
            System.out.println("Minor savings demo seeder: no ACTIVE members found.");
            return;
        }

        List<MinorSavingsAccount> created = new ArrayList<>();
        int skipped = 0;

        for (Member member : members) {
            String memberId = member.getMemberId();

            // Idempotent per member. Anyone who already has accounts - seeded
            // earlier, or entered by hand - is left exactly as they are.
            if (!minorSavingsAccountRepository.findByMemberId(memberId).isEmpty()) {
                skipped++;
                continue;
            }

            String token = accountToken(memberId);
            int accountCount = 1 + Math.floorMod(memberId.hashCode(), 3);

            for (int n = 1; n <= accountCount; n++) {
                created.add(buildAccount(memberId, token, n));
            }
        }

        if (created.isEmpty()) {
            System.out.println(
                    "Minor savings demo seeder: nothing to do - all "
                            + members.size() + " ACTIVE members already have accounts.");
            return;
        }

        assertNoDuplicates(created);
        minorSavingsAccountRepository.saveAll(created);

        System.out.println(
                "Minor savings demo seeder: created " + created.size()
                        + " account(s) across " + (members.size() - skipped) + " member(s); "
                        + skipped + " already had accounts.");
        System.out.println(
                "Minor savings demo seeder: set minor.savings.seed.demo=false again, "
                        + "and remember these members now require the mandatory "
                        + "'Minor Savings Account Disbursement Instruction' document.");
    }

    private MinorSavingsAccount buildAccount(String memberId, String token, int n) {
        MinorSavingsAccount account = new MinorSavingsAccount();
        account.setMinorAccountNo(ACCOUNT_PREFIX + token + "-" + n);
        account.setMemberId(memberId);
        account.setHolderName(holderName(memberId, n));
        account.setBirthCertificateNo("BC-DEMO-" + token + "-" + n);
        account.setBalance(balance(memberId, n));
        return account;
    }

    /**
     * Everything varies off the member id rather than off a random source, so a member
     * always gets the same accounts, balances and holders. A re-seeded database stays
     * comparable to the one a screenshot was taken from.
     */
    private String holderName(String memberId, int n) {
        int index = Math.floorMod(memberId.hashCode() + n, HOLDER_NAMES.size());
        return HOLDER_NAMES.get(index);
    }

    private float balance(String memberId, int n) {
        int spread = MAX_BALANCE - MIN_BALANCE;
        int offset = Math.floorMod(memberId.hashCode() * 31 + n * 7919, spread);
        // Rounded to the nearest 500 so the figures read like passbook balances
        // rather than the hash they are derived from.
        return Math.round((MIN_BALANCE + offset) / 500f) * 500f;
    }

    /**
     * A per-member token for the generated account numbers: the member id with its
     * "MEM-" prefix dropped and the separators removed, so MEM-DEMO-013 becomes
     * DEMO013 and MEM-2026-001 becomes 2026001.
     *
     * Deliberately NOT just the tail of the id. That would reduce both MEM-DEMO-001
     * and MEM-2026-001 to "001", and since minor_account_no is the primary key the
     * second row would silently overwrite the first rather than fail - one member
     * ending up with the other's account. Keeping the whole discriminating part of
     * the id is what makes the number unique; assertNoDuplicates() then proves it
     * rather than trusting it.
     */
    private String accountToken(String memberId) {
        String withoutPrefix = memberId.startsWith(MEMBER_PREFIX)
                ? memberId.substring(MEMBER_PREFIX.length())
                : memberId;
        return withoutPrefix.replaceAll("[^A-Za-z0-9]", "");
    }

    /**
     * Last line of defence for the primary key. Two members whose ids differ only by
     * separators would still produce the same token, and saveAll() would treat the
     * clash as an update. Failing the boot is the right outcome: it is a data bug that
     * needs a look, not something to paper over at 3am with a suffix bump.
     */
    private void assertNoDuplicates(List<MinorSavingsAccount> accounts) {
        Set<String> seen = new HashSet<>();
        for (MinorSavingsAccount account : accounts) {
            if (!seen.add(account.getMinorAccountNo())) {
                throw new IllegalStateException(
                        "Minor savings demo seeder: duplicate account number "
                                + account.getMinorAccountNo()
                                + " - two member ids collapsed to the same token. "
                                + "Nothing was written.");
            }
        }
    }
}
