package com.memberconnect.backend.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.repository.MemberTransferRepository;

/**
 * One-time backfill of member_transfer_request.submission_location.
 *
 * MMC28's Location filter and the server-side scope both key on this column, and a row
 * without one is hidden from a location-restricted caller. Every transfer request
 * created before the column existed has none, so without this a District Office user
 * would open the list and find it empty - the records are there, but invisible to them.
 *
 * The value comes from the member's own administering office, which is where the
 * request would have been stamped had the column existed when it was raised. A member
 * carrying no office of their own is left null rather than guessed at: the user who
 * raised the request is not recorded anywhere, so there is nothing to derive it from,
 * and an invented district would misfile the request under an office that never saw it.
 *
 * Idempotent - it only ever fills a blank - so this is a no-op on every boot after the
 * first, and it never overwrites a location already set.
 */
@Component
@Order(10)
public class MemberTransferLocationBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MemberTransferLocationBackfill.class);

    private final MemberTransferRepository memberTransferRepository;

    public MemberTransferLocationBackfill(MemberTransferRepository memberTransferRepository) {
        this.memberTransferRepository = memberTransferRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<MemberTransferRequest> requests = memberTransferRepository.findAll().stream()
                .filter(request -> isBlank(request.getSubmissionLocation()))
                .toList();

        if (requests.isEmpty()) {
            return;
        }

        int filled = 0;
        int unresolved = 0;

        for (MemberTransferRequest request : requests) {
            Member member = request.getMember();
            String location = member == null ? null : member.getSubmissionLocation();

            if (isBlank(location)) {
                unresolved++;
                continue;
            }

            request.setSubmissionLocation(location);
            memberTransferRepository.save(request);
            filled++;
        }

        log.info(
                "Member transfer submission locations backfilled: {} of {} untagged requests filled"
                        + " from the member's office, {} left null because the member carries none"
                        + " (those stay visible to Head Office and hidden from district users)",
                filled, requests.size(), unresolved
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
