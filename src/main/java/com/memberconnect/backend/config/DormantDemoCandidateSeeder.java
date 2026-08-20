package com.memberconnect.backend.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.DormantConfig;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * Ages a few Active members past the dormancy cutoff so MMD10 has something to
 * find on a demo database.
 *
 * <h2>Why this is needed at all</h2>
 *
 * Every member in the demo data joined within the last few months, so against a
 * twelve-month cutoff none of them is genuinely dormant and Run Identification
 * correctly reports nothing. That is the feature working, not failing - but it
 * makes the whole of section 4 impossible to demonstrate without waiting a year.
 *
 * <h2>Why it writes the repository directly instead of using MemberActivityService</h2>
 *
 * MemberActivityService.recordActivity() never moves lastActivityDate backwards,
 * so that the Finance Module can replay a month without un-ageing an account that
 * has since transacted. That guard is right for a live feed and is exactly what
 * blocks this: the superseded DormantDataSeeder used to stamp
 * lastActivityDate = today on every member at every startup, and nothing that
 * respects the forward-only rule can undo it. Repairing data the old seeder
 * poisoned is a different job from recording activity, so it is done here,
 * deliberately and in one clearly-labelled place, rather than by weakening the
 * rule that protects the real path.
 *
 * <h2>Off by default</h2>
 *
 * Enable with {@code dormant.seed.demo-candidates=true} (or the environment
 * variable DORMANT_SEED_DEMO_CANDIDATES=true) for a single run, then turn it off.
 * It only ever touches members whose id starts with MEM-DEMO-, so it cannot
 * reach a real membership record, and it skips any member already flagged.
 */
@Component
@Order(4)
public class DormantDemoCandidateSeeder implements CommandLineRunner {

    /** Only demo records are eligible. A real member id will never match. */
    private static final String DEMO_PREFIX = "MEM-DEMO-";

    private final MemberRepository memberRepository;
    private final DormantMembershipService dormantService;
    private final boolean enabled;
    private final int candidateCount;

    public DormantDemoCandidateSeeder(
            MemberRepository memberRepository,
            DormantMembershipService dormantService,
            @Value("${dormant.seed.demo-candidates:false}") boolean enabled,
            @Value("${dormant.seed.demo-candidate-count:3}") int candidateCount
    ) {
        this.memberRepository = memberRepository;
        this.dormantService = dormantService;
        this.enabled = enabled;
        this.candidateCount = candidateCount;
    }

    @Override
    public void run(String... args) {
        // Says so either way. A seeder that is silent when switched off is
        // indistinguishable from one that never ran, which is exactly the
        // confusion this class exists to resolve.
        System.out.println("Dormant demo seeder: enabled=" + enabled);

        if (!enabled) {
            return;
        }

        DormantConfig config = dormantService.getConfig();

        // Comfortably past the cutoff rather than exactly on it, so the demo does
        // not turn on an off-by-one at the boundary.
        LocalDate staleDate = LocalDate.now()
                .minusMonths(config.getDormantPeriodMonths())
                .minusMonths(2);

        // Taken from the END of the demo range, highest id first. Ascending order
        // put the low-numbered members first, and those are the ones the other
        // module demos are built around - MEM-DEMO-008 carries the minor savings
        // accounts and bank details the death-record walkthrough uses. Flagging
        // one of those as dormant takes it out of ACTIVE and breaks the other
        // demo, so dormancy claims the filler records at the tail instead.
        List<Member> candidates = memberRepository.findByStatus(MemberStatus.ACTIVE).stream()
                .filter(m -> m.getMemberId() != null && m.getMemberId().startsWith(DEMO_PREFIX))
                .sorted((a, b) -> b.getMemberId().compareTo(a.getMemberId()))
                .limit(Math.max(1, candidateCount))
                .toList();

        if (candidates.isEmpty()) {
            System.out.println("Dormant demo seeder: no eligible " + DEMO_PREFIX + " members found.");
            return;
        }

        for (Member member : candidates) {
            member.setLastActivityDate(staleDate);
            memberRepository.save(member);
            System.out.println(
                    "Dormant demo seeder: aged " + member.getMemberId()
                            + " to lastActivityDate=" + staleDate);
        }

        System.out.println(
                "Dormant demo seeder: " + candidates.size()
                        + " member(s) are now past the " + config.getDormantPeriodMonths()
                        + "-month cutoff. Run the identification process to flag them.");
    }
}
