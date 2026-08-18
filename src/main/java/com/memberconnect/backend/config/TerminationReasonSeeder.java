package com.memberconnect.backend.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.TerminationReason;
import com.memberconnect.backend.repository.TerminationReasonRepository;

/**
 * Seeds the Termination Reasons Master with the four SRS values, using the same
 * count-guard as BankDataSeeder and MemberDeathRecordService.seedCauseOfDeathIfEmpty():
 * once the master holds any row it is left completely alone, so a restart can
 * never duplicate, rename or reactivate a reason an administrator has edited.
 */
@Component
@Order(2)
public class TerminationReasonSeeder implements CommandLineRunner {

    private final TerminationReasonRepository terminationReasonRepository;

    public TerminationReasonSeeder(TerminationReasonRepository terminationReasonRepository) {
        this.terminationReasonRepository = terminationReasonRepository;
    }

    @Override
    public void run(String... args) {
        if (terminationReasonRepository.count() > 0) {
            return;
        }

        terminationReasonRepository.saveAll(List.of(
                createReason("RESIGNATION", "Resignation from Post", 1),
                createReason("DISCIPLINARY", "Disciplinary Action", 2),
                createReason("TRANSFER", "Transfer to Another Organization", 3),
                createReason("OTHER", "Other", 4)
        ));

        System.out.println("Seeded default termination reasons.");
    }

    private TerminationReason createReason(String code, String name, int displayOrder) {
        TerminationReason reason = new TerminationReason();
        reason.setCode(code);
        reason.setName(name);
        reason.setActive(true);
        reason.setDisplayOrder(displayOrder);
        return reason;
    }
}
