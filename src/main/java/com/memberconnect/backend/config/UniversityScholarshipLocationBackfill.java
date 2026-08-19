package com.memberconnect.backend.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;

/**
 * One-time backfill of UniversityScholarshipRequest.SubmissionLocation.
 *
 * Requests created before the Location filter existed have no District Office on
 * them. Without this, switching on location scoping would make every historical
 * request invisible to District Office users. The value is taken from the member's
 * own SubmissionLocation — the same source used when a new request is saved — so
 * backfilled rows sort exactly as freshly created ones do.
 *
 * The member is already a @ManyToOne on the request, so unlike the Grade 5 backfill
 * this needs no second lookup.
 *
 * Rows whose member has no location recorded are left null. Null is read as "not
 * location-tagged" and stays visible to everyone rather than disappearing, so an
 * incomplete backfill degrades safely.
 *
 * Safe to run on every start: it only touches rows that are still null.
 */
@Component
public class UniversityScholarshipLocationBackfill implements ApplicationRunner {

    @Autowired
    private UniversityScholarshipRequestRepository scholarshipRequestRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<UniversityScholarshipRequest> untagged = scholarshipRequestRepository.findAll().stream()
                .filter(request -> request.getSubmissionLocation() == null
                        || request.getSubmissionLocation().isBlank())
                .toList();

        if (untagged.isEmpty()) {
            return;
        }

        int tagged = 0;
        for (UniversityScholarshipRequest request : untagged) {
            if (request.getMember() == null) {
                continue;
            }

            String location = request.getMember().getSubmissionLocation();
            if (location == null || location.isBlank()) {
                continue;
            }

            request.setSubmissionLocation(location);

            // Backfilled rows have no real creation audit; record when they were
            // tagged rather than inventing a creation time that never happened.
            if (request.getCreatedAt() == null) {
                request.setCreatedAt(LocalDateTime.now());
                request.setCreatedBy("system-backfill");
            }

            scholarshipRequestRepository.save(request);
            tagged++;
        }

        System.out.println("[university-scholarship-backfill] SubmissionLocation set on " + tagged
                + " of " + untagged.size() + " untagged University Scholarship requests"
                + (tagged < untagged.size()
                        ? "; the remainder have no member location on file and stay visible to all locations"
                        : ""));
    }
}
