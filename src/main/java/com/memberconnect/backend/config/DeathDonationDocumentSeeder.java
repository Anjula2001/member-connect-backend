package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.repository.RequiredDocumentRepository;

/**
 * Seeds the Supporting Documents for Applications Master with the Death Donation
 * rows (SRS MMD01): "The mandatory document types to be uploaded for a Death
 * Donation Request will be pre-defined and the system user must upload the files
 * to the mandatory document types before submitting the request for approval."
 *
 * Before this existed the required set was a hardcoded constant in
 * DeathDonationService, which meant the "Required Documents" grid the SRS
 * describes as coming from the Supporting Documents master was in fact coming
 * from a Java literal, and an administrator could not change it.
 *
 * The guard is scoped to this seeder's own application type rather than a
 * whole-table count, because required_document is shared with termination,
 * retirement, member death and the member application. An administrator who
 * edits these rows therefore keeps their changes - the seeder only ever fills a
 * completely empty set.
 *
 * On the document names: the SRS names only the death certificate explicitly and
 * says the rest is pre-defined configuration. The remaining rows are a starter
 * set to be confirmed against the client's master list before going to
 * production.
 */
@Component
@Order(3)
public class DeathDonationDocumentSeeder implements CommandLineRunner {

    /** Death Donation Request Entry (MMD01). */
    public static final String DEATH_DONATION = "DEATH_DONATION";

    private final RequiredDocumentRepository requiredDocumentRepository;

    public DeathDonationDocumentSeeder(RequiredDocumentRepository requiredDocumentRepository) {
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    /**
     * The stable code an upload is filed under, derived from the display name.
     *
     * Uploads store a code ("DEATH_CERTIFICATE"), not a master row id, so the two
     * are bridged here rather than by a schema change that would strand every
     * document already uploaded. Kept in one place so the service and the seeder
     * cannot disagree about what a name maps to.
     */
    public static String toDocumentTypeCode(String documentName) {
        if (documentName == null) {
            return null;
        }
        String code = documentName.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return code.replaceAll("^_+|_+$", "");
    }

    @Override
    public void run(String... args) {
        if (!requiredDocumentRepository.findByApplicationType(DEATH_DONATION).isEmpty()) {
            return;
        }

        List<RequiredDocument> documents = new ArrayList<>();
        documents.add(create("Death Certificate", true));
        documents.add(create("NIC Copy", true));
        documents.add(create("Other", false));

        requiredDocumentRepository.saveAll(documents);
        System.out.println("Seeded " + documents.size() + " death donation required documents.");
    }

    private RequiredDocument create(String documentName, boolean mandatory) {
        RequiredDocument document = new RequiredDocument();
        document.setApplicationType(DEATH_DONATION);
        document.setDocumentName(documentName);
        document.setMandatory(mandatory);
        return document;
    }
}
