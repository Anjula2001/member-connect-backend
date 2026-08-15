package com.memberconnect.backend.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.MemberRepository;
import com.memberconnect.backend.service.DormantMembershipService;

/**
 * Seeds the default dormant configuration and backfills a last-activity date
 * for any member that does not have one yet (treated as recently active so they
 * are not immediately flagged as dormant).
 */
@Component
@Order(3)
public class DormantDataSeeder implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final DormantMembershipService dormantService;

    public DormantDataSeeder(MemberRepository memberRepository, DormantMembershipService dormantService) {
        this.memberRepository = memberRepository;
        this.dormantService = dormantService;
    }

    @Override
    public void run(String... args) {
        dormantService.seedDefaultConfigIfEmpty();

        LocalDate today = LocalDate.now();
        List<Member> members = memberRepository.findAll();
        int updated = 0;
        for (Member member : members) {
            if (member.getLastActivityDate() == null) {
                member.setLastActivityDate(today);
                memberRepository.save(member);
                updated++;
            }
        }
        if (updated > 0) {
            System.out.println("Backfilled last-activity date for " + updated + " member(s).");
        }
    }
}
