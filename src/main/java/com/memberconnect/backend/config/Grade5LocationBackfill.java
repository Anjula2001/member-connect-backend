package com.memberconnect.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.Grade5ScholarshipRequest;
import com.memberconnect.backend.repository.Grade5ScholarshipRepository;
import com.memberconnect.backend.repository.MemberRepository;

/**
 * One-time backfill of Grade5ScholarshipRequest.SubmissionLocation.
 *
 * Requests created before the Location filter existed have no District Office on
 * them. Without this, switching on location scoping would make every historical
 * request invisible to District Office users. The value is taken from the member's
 * own SubmissionLocation — the same source used when a new request is saved — so
 * backfilled rows sort exactly as freshly created ones do.
 *
 * Rows whose member cannot be resolved, or whose member has no location recorded,
 * are left null. Null is read as "not location-tagged" and stays visible to everyone
 * rather than disappearing, so an incomplete backfill degrades safely.
 *
 * Safe to run on every start: it only touches rows that are still null, so once the
 * data is tagged this becomes a no-op.
 */
@Component
public class Grade5LocationBackfill implements ApplicationRunner {

    @Autowired
    private Grade5ScholarshipRepository grade5ScholarshipRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<Grade5ScholarshipRequest> untagged = grade5ScholarshipRepository.findAll().stream()
                .filter(request -> request.getSubmissionLocation() == null
                        || request.getSubmissionLocation().isBlank())
                .toList();

        if (untagged.isEmpty()) {
            return;
        }

        int tagged = 0;
        for (Grade5ScholarshipRequest request : untagged) {
            if (request.getMemberId() == null) {
                continue;
            }

            String location = memberRepository.findByMemberId(request.getMemberId())
                    .map(member -> member.getSubmissionLocation())
                    .orElse(null);

            if (location == null || location.isBlank()) {
                continue;
            }

            request.setSubmissionLocation(location);
            grade5ScholarshipRepository.save(request);
            tagged++;
        }

        System.out.println("[grade5-backfill] SubmissionLocation set on " + tagged
                + " of " + untagged.size() + " untagged Grade 5 requests"
                + (tagged < untagged.size()
                        ? "; the remainder have no member location on file and stay visible to all locations"
                        : ""));
    }
}
