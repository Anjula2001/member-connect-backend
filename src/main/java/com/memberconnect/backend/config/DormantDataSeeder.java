package com.memberconnect.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * Seeds the default dormant configuration, and - once, on request - backfills a
 * last-activity date for members that have none.
 *
 * <h2>Why the backfill is now opt-in and no longer uses today's date</h2>
 *
 * This class previously set {@code lastActivityDate = today} for every member
 * whose value was null, unconditionally, on every startup. Both halves of that
 * were wrong, and together they silently disabled MMD10:
 *
 * <ul>
 *   <li><b>Every startup.</b> A member who was correctly ageing towards the
 *       dormancy cutoff had their clock reset by the next restart, so on a
 *       database where the column was never populated the cutoff could never be
 *       crossed and the identification process could never flag anybody.</li>
 *   <li><b>Today's date.</b> Today is the latest possible reading - it asserts
 *       that every member with no recorded history transacted this morning. The
 *       membership start date is the earliest defensible one: it is the only
 *       moment we know for certain the member was active.</li>
 * </ul>
 *
 * The service now falls back to {@code membershipStartDate} at read time
 * (see DormantMembershipService.activityAnchor), so in normal running this
 * backfill is not needed at all. It is kept, behind a flag that defaults to off,
 * for the one-off case of populating the column explicitly on an existing
 * database.
 */
@Component
@Order(3)
public class DormantDataSeeder implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final DormantMembershipService dormantService;
    private final boolean backfillActivity;

    public DormantDataSeeder(
            MemberRepository memberRepository,
            DormantMembershipService dormantService,
            @Value("${dormant.seed.backfill-activity:false}") boolean backfillActivity
    ) {
        this.memberRepository = memberRepository;
        this.dormantService = dormantService;
        this.backfillActivity = backfillActivity;
    }

    @Override
    public void run(String... args) {
        dormantService.seedDefaultConfigIfEmpty();

        if (!backfillActivity) {
            return;
        }

        List<Member> members = memberRepository.findAll();
        int updated = 0;

        for (Member member : members) {
            if (member.getLastActivityDate() == null && member.getMembershipStartDate() != null) {
                member.setLastActivityDate(member.getMembershipStartDate());
                memberRepository.save(member);
                updated++;
            }
        }

        if (updated > 0) {
            System.out.println(
                    "Backfilled last-activity date from membership start date for "
                            + updated + " member(s).");
        }
    }
}
