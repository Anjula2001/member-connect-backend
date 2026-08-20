package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.repository.RequiredDocumentRepository;

/**
 * Seeds the Supporting Documents for Applications Master with the termination rows.
 *
 * Without these rows the module has a silent hole: TerminationService.submitRequest()
 * gates on DocumentService.allMandatoryDocumentsUploaded(), which is
 * {@code requiredDocuments.stream().filter(mandatory).allMatch(uploaded)} - and
 * allMatch on an empty stream is true. So on any database that has no TERMINATION rows
 * the mandatory-document check passes vacuously and a request can be submitted with no
 * documents at all, exactly the opposite of MMT01.
 *
 * Deliberately NOT copying TerminationReasonSeeder's {@code count() > 0} guard: the
 * required_document table is shared and already holds RETIREMENT and member-application
 * rows, so a whole-table count is never zero and that guard would make this seeder a
 * permanent no-op. The guard here is scoped to the termination application types only,
 * which also means an administrator who edits or deletes these rows keeps their
 * changes - the seeder only ever fills a completely empty set.
 *
 * On the document names: the SRS names only the "termination request letter" explicitly
 * (MMT01) and says the rest of the list is pre-defined configuration, with the set
 * growing when the member has minor savings accounts to close. The remaining rows below
 * are therefore a starter set to be confirmed against the client's master list. They
 * become live master data that uploads reference by id, so correct them here before
 * going to production rather than after uploads exist.
 */
@Component
@Order(3)
public class TerminationDocumentSeeder implements CommandLineRunner {

    /** Termination Request Entry (MMT01). */
    static final String TERMINATION = "TERMINATION";

    /**
     * Additional documents required only when the member has minor savings accounts
     * that need closing. DocumentService.getRequiredDocuments() appends this type
     * automatically when the member has any MinorSavingsAccount row.
     */
    static final String TERMINATION_MINOR = "TERMINATION_MINOR";

    /**
     * The scanned, board-signed 'Termination Request List for Board Approval' report,
     * attached to the approval list rather than to a single request (MMT09). Optional:
     * the board decision must be recordable before the signed page comes back from the
     * scanner.
     */
    static final String TERMINATION_APPROVAL_REPORT = "TERMINATION_APPROVAL_REPORT";

    private static final List<String> TERMINATION_APPLICATION_TYPES =
            List.of(TERMINATION, TERMINATION_MINOR, TERMINATION_APPROVAL_REPORT);

    private final RequiredDocumentRepository requiredDocumentRepository;

    public TerminationDocumentSeeder(RequiredDocumentRepository requiredDocumentRepository) {
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    @Override
    public void run(String... args) {
        if (!requiredDocumentRepository.findByApplicationTypeIn(TERMINATION_APPLICATION_TYPES).isEmpty()) {
            return;
        }

        List<RequiredDocument> documents = new ArrayList<>();

        // Named in the SRS.
        documents.add(create(TERMINATION, "Termination Request Letter", true));
        documents.add(create(TERMINATION, "Membership Documents Handover Acknowledgement", true));
        documents.add(create(TERMINATION, "Other Supporting Documents", false));

        // Optional by product decision, not by the SRS. MMT01 describes the minor
        // savings paperwork as mandatory, and the equivalent row in
        // MemberDeathDocumentSeeder is still created mandatory. It was made optional
        // here because the requirement appears the instant a member has any minor
        // savings account, which blocked termination requests that were otherwise
        // complete. The disbursement bank details on the request itself are still
        // required - TerminationService validates those independently of this row -
        // so the instruction remains a document to collect, just not a submit gate.
        documents.add(create(TERMINATION_MINOR, "Minor Savings Account Disbursement Instruction", false));
        documents.add(create(TERMINATION_MINOR, "Minor Bank Account Confirmation", false));

        documents.add(create(TERMINATION_APPROVAL_REPORT, "Signed Termination Approval List", false));

        requiredDocumentRepository.saveAll(documents);

        System.out.println("Seeded " + documents.size() + " termination required documents.");
    }

    private RequiredDocument create(String applicationType, String documentName, boolean mandatory) {
        RequiredDocument document = new RequiredDocument();
        document.setApplicationType(applicationType);
        document.setDocumentName(documentName);
        document.setMandatory(mandatory);
        return document;
    }
}
