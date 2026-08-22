package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.repository.RequiredDocumentRepository;

/**
 * Seeds the Supporting Documents for Applications Master with the Record Member
 * Death rows (SRS MMT18).
 *
 * Without these rows the module has the same silent hole the termination seeder
 * documents: MemberDeathRecordService.submitRecord() gates on
 * DocumentService.allMandatoryDocumentsUploaded(), which is
 * {@code requiredDocuments.stream().filter(mandatory).allMatch(uploaded)} - and
 * allMatch on an empty stream is true. On any database with no MEMBER_DEATH rows
 * the mandatory-document check would pass vacuously and a death record could be
 * submitted with no death certificate at all.
 *
 * The guard is scoped to this seeder's own application types rather than a
 * whole-table count, because required_document is shared with termination,
 * retirement and the member application. That also means an administrator who
 * edits or deletes these rows keeps their changes - the seeder only ever fills a
 * completely empty set.
 *
 * On the document names: the SRS names only the Death Certificate explicitly and
 * says the rest is pre-defined configuration, with the set growing when the
 * member has minor savings accounts to close. The remaining rows are a starter
 * set to be confirmed against the client's master list. They become live master
 * data that uploads reference by id, so correct them here before going to
 * production rather than after uploads exist.
 */
@Component
@Order(3)
public class MemberDeathDocumentSeeder implements CommandLineRunner {

    /** Record Member Death Entry (MMT18). */
    public static final String MEMBER_DEATH = "MEMBER_DEATH";

    /**
     * Additional documents required only when the member has minor savings
     * accounts that need closing. DocumentService.getRequiredDocuments() appends
     * this type automatically when the member has any MinorSavingsAccount row.
     */
    public static final String MEMBER_DEATH_MINOR = "MEMBER_DEATH_MINOR";

    private static final List<String> MEMBER_DEATH_APPLICATION_TYPES =
            List.of(MEMBER_DEATH, MEMBER_DEATH_MINOR);

    private final RequiredDocumentRepository requiredDocumentRepository;

    public MemberDeathDocumentSeeder(RequiredDocumentRepository requiredDocumentRepository) {
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    @Override
    public void run(String... args) {
        if (!requiredDocumentRepository.findByApplicationTypeIn(MEMBER_DEATH_APPLICATION_TYPES).isEmpty()) {
            return;
        }

        List<RequiredDocument> documents = new ArrayList<>();

        // Named in the SRS: the nominee "submits the Death Certificate".
        documents.add(create(MEMBER_DEATH, "Death Certificate", true));
        documents.add(create(MEMBER_DEATH, "Nominee Identification", true));
        documents.add(create(MEMBER_DEATH, "Membership Documents Handover Acknowledgement", false));
        documents.add(create(MEMBER_DEATH, "Other Supporting Documents", false));

        documents.add(create(MEMBER_DEATH_MINOR, "Minor Savings Account Disbursement Instruction", true));
        documents.add(create(MEMBER_DEATH_MINOR, "Minor Bank Account Confirmation", false));

        requiredDocumentRepository.saveAll(documents);

        System.out.println("Seeded " + documents.size() + " member death required documents.");
    }

    private RequiredDocument create(String applicationType, String documentName, boolean mandatory) {
        RequiredDocument document = new RequiredDocument();
        document.setApplicationType(applicationType);
        document.setDocumentName(documentName);
        document.setMandatory(mandatory);
        return document;
    }
}
