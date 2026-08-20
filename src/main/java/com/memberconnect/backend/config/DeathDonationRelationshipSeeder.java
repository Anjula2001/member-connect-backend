package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.DeathDonationRelationship;
import com.memberconnect.backend.repository.DeathDonationRelationshipRepository;

/**
 * Seeds the Death Donation Relationship master (SRS MMD01).
 *
 * The values below mirror the list the entry screen previously hardcoded, so
 * existing requests keep matching after the dropdown starts reading from here.
 * They are a starter set: confirm them against the client's master list before
 * going to production, since saved requests reference them by name.
 *
 * Like the other masters, the guard is an emptiness check rather than a per-row
 * upsert, so an administrator who edits or removes a relationship keeps their
 * change instead of having it reinstated on the next boot.
 */
@Component
@Order(4)
public class DeathDonationRelationshipSeeder implements CommandLineRunner {

    private final DeathDonationRelationshipRepository relationshipRepository;

    public DeathDonationRelationshipSeeder(DeathDonationRelationshipRepository relationshipRepository) {
        this.relationshipRepository = relationshipRepository;
    }

    @Override
    public void run(String... args) {
        if (!relationshipRepository.findAll().isEmpty()) {
            return;
        }

        String[] names = {
                "Father", "Mother", "Spouse", "Son", "Daughter",
                "Brother", "Sister", "Grandfather", "Grandmother", "Other"
        };

        List<DeathDonationRelationship> relationships = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            DeathDonationRelationship relationship = new DeathDonationRelationship();
            relationship.setCode(names[index].toUpperCase());
            relationship.setName(names[index]);
            relationship.setDisplayOrder(index + 1);
            relationship.setActive(true);
            relationships.add(relationship);
        }

        relationshipRepository.saveAll(relationships);
        System.out.println("Seeded " + relationships.size() + " death donation relationships.");
    }
}
