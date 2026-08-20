package com.memberconnect.backend.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.UniversityScholarshipRequest;
import com.memberconnect.backend.repository.UniversityScholarshipRequestRepository;

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
