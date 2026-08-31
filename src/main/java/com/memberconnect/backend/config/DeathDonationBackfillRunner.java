package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.DeathDonationRequest;
import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.repository.DeathDonationRequestRepository;

/**
 * One-time backfill of {@code submission_location} on Death Donation Requests
 * raised before district scoping existed.
 *
 * The MMD02 search now scopes a District Office user to their own assigned
 * district and treats a null location as invisible, so without this every
 * historic request would vanish from the district that raised it - the same
 * failure MemberDeathBackfillRunner was written to prevent.
 *
 * The member's own submission location is the only signal available after the
 * fact. It is not always right - SRS p.12 lets a member raise a request at any
 * office, so a request taken elsewhere will be attributed to the member's home
 * district - but it is far better than leaving the row invisible, and it only
 * ever applies where the column is currently empty.
 *
 * Idempotent: it fills gaps and never overwrites, so this is a no-op on every
 * boot after the first.
 */
@Component
@Order(6)
public class DeathDonationBackfillRunner implements CommandLineRunner {

    private final DeathDonationRequestRepository requestRepository;

    public DeathDonationBackfillRunner(DeathDonationRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Override
    public void run(String... args) {
        List<DeathDonationRequest> missing = new ArrayList<>();

        // findAllWithMember, not findAll: a CommandLineRunner runs outside any
        // transaction, so the LAZY member would be a proxy with no session behind
        // it and reading it throws LazyInitializationException.
        for (DeathDonationRequest request : requestRepository.findAllWithMember()) {
            if (request.getSubmissionLocation() != null && !request.getSubmissionLocation().isBlank()) {
                continue;
            }

            Member member = request.getMember();
            if (member == null) {
                continue;
            }

            String location = member.getSubmissionLocation();
            if (location == null || location.isBlank()) {
                continue;
            }

            request.setSubmissionLocation(location);
            missing.add(request);
        }

        if (missing.isEmpty()) {
            return;
        }

        requestRepository.saveAll(missing);
        System.out.println(
                "Backfilled submission location on " + missing.size() + " death donation requests.");
    }
}
