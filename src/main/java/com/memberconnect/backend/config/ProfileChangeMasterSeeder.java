package com.memberconnect.backend.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.NomineeRelationship;
import com.memberconnect.backend.model.Title;
import com.memberconnect.backend.repository.NomineeRelationshipRepository;
import com.memberconnect.backend.repository.TitleRepository;

/**
 * Seeds the two masters Requirement 02 names but the schema did not have:
 * the Title Master (MMC05) and the Nominee Relationship Master (MMC18).
 *
 * Both were previously free-text or hardcoded in the frontend - the nominee
 * relationship list existed only as four lowercase literals inside the React
 * component, which meant it could not be extended without a release and did not
 * match the casing stored on Member.nomineeRelationship.
 *
 * Each master is guarded independently: seeding one must not be skipped because
 * the other already has rows. As elsewhere, the guard only fills an empty set, so
 * an administrator's edits survive a restart.
 *
 * The Nominee Identification Type Master the SRS also mentions is deliberately not
 * created here - enums/Identification already carries exactly that list
 * (NIC, Passport, DrivingLicense, BirthCertificate) and is what Member already uses.
 */
@Component
@Order(4)
public class ProfileChangeMasterSeeder implements CommandLineRunner {

    private final TitleRepository titleRepository;
    private final NomineeRelationshipRepository nomineeRelationshipRepository;

    public ProfileChangeMasterSeeder(
            TitleRepository titleRepository,
            NomineeRelationshipRepository nomineeRelationshipRepository
    ) {
        this.titleRepository = titleRepository;
        this.nomineeRelationshipRepository = nomineeRelationshipRepository;
    }

    @Override
    public void run(String... args) {
        seedTitles();
        seedNomineeRelationships();
    }

    private void seedTitles() {
        if (titleRepository.count() > 0) {
            return;
        }

        List<String> names = List.of("Mr.", "Mrs.", "Miss", "Ms.", "Rev.", "Dr.", "Prof.");

        int order = 1;
        for (String name : names) {
            Title title = new Title();
            title.setName(name);
            title.setDisplayOrder(order++);
            title.setActive(true);
            titleRepository.save(title);
        }

        System.out.println("Seeded " + names.size() + " titles.");
    }

    private void seedNomineeRelationships() {
        if (nomineeRelationshipRepository.count() > 0) {
            return;
        }

        // Supersedes the spouse/child/parent/sibling list that was hardcoded in
        // NommineChangeRequest/page.tsx, widened to the relationships a nominee is
        // realistically named from.
        List<String> names = List.of(
                "Spouse", "Son", "Daughter", "Father", "Mother",
                "Brother", "Sister", "Guardian", "Other"
        );

        int order = 1;
        for (String name : names) {
            NomineeRelationship relationship = new NomineeRelationship();
            relationship.setName(name);
            relationship.setDisplayOrder(order++);
            relationship.setActive(true);
            nomineeRelationshipRepository.save(relationship);
        }

        System.out.println("Seeded " + names.size() + " nominee relationships.");
    }
}
