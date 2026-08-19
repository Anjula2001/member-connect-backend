package com.memberconnect.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.enums.ProfileChangeType;
import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.repository.RequiredDocumentRepository;

/**
 * Seeds the Supporting Documents for Applications Master with the four Member
 * Profile Change request types (Requirement 02, MMC01 / MMC05 / MMC14 / MMC18).
 *
 * Each of those functions states that "the mandatory document types to be uploaded
 * ... will be pre-defined and the system user must upload the files to the mandatory
 * document types before submitting the request". Submit gates on
 * DocumentService.allMandatoryDocumentsUploaded(), which is an allMatch over the
 * mandatory rows - and allMatch on an empty stream is true. So without these rows
 * the gate passes vacuously and a request can be submitted with no documents at all,
 * exactly the hole TerminationDocumentSeeder was written to close for terminations.
 *
 * The guard is scoped to these application types rather than a whole-table
 * count() > 0, because required_document is shared and already holds RETIREMENT,
 * TERMINATION and member-application rows - a table-wide guard would make this
 * seeder a permanent no-op. Scoping it also means an administrator who edits or
 * deletes these rows keeps their changes; the seeder only ever fills an empty set.
 *
 * On the document names: the SRS does not enumerate them for any of the four types,
 * it only says the list is pre-defined configuration. The names below are taken from
 * the lists the existing request screens were already displaying to users, so they
 * are a faithful starter set rather than an invention - but they are live master data
 * that uploads reference by id, so confirm them against the client's master list
 * before production rather than after uploads exist.
 */
@Component
@Order(4)
public class ProfileChangeDocumentSeeder implements CommandLineRunner {

    /**
     * The scanned, board-signed 'Name Change Request List for Board Approval' and
     * 'Nominee Change Request List for Board Approval' reports (Requirement 02
     * section 7). These attach to the approval list rather than to a single request,
     * and are optional: the board decision must be recordable before the signed page
     * comes back from the scanner.
     */
    static final String NAME_CHANGE_APPROVAL_REPORT = "NAME_CHANGE_APPROVAL_REPORT";
    static final String NOMINEE_CHANGE_APPROVAL_REPORT = "NOMINEE_CHANGE_APPROVAL_REPORT";

    private static final List<String> PROFILE_CHANGE_APPLICATION_TYPES = List.of(
            ProfileChangeType.BASIC_PROFILE.getDocumentType(),
            ProfileChangeType.NAME.getDocumentType(),
            ProfileChangeType.NOMINEE.getDocumentType(),
            ProfileChangeType.REMITTANCE.getDocumentType(),
            NAME_CHANGE_APPROVAL_REPORT,
            NOMINEE_CHANGE_APPROVAL_REPORT
    );

    private final RequiredDocumentRepository requiredDocumentRepository;

    public ProfileChangeDocumentSeeder(RequiredDocumentRepository requiredDocumentRepository) {
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    @Override
    public void run(String... args) {
        if (!requiredDocumentRepository.findByApplicationTypeIn(PROFILE_CHANGE_APPLICATION_TYPES).isEmpty()) {
            return;
        }

        String basicProfile = ProfileChangeType.BASIC_PROFILE.getDocumentType();
        String nameChange = ProfileChangeType.NAME.getDocumentType();
        String nomineeChange = ProfileChangeType.NOMINEE.getDocumentType();
        String remittanceChange = ProfileChangeType.REMITTANCE.getDocumentType();

        List<RequiredDocument> documents = new ArrayList<>();

        // MMC01 - the NIC copy is the one document that evidences most of the fields
        // this request can change (date of birth, NIC, gender, name spelling).
        documents.add(create(basicProfile, "NIC Copy", true));
        documents.add(create(basicProfile, "Request Letter from Member", true));
        documents.add(create(basicProfile, "Other Supporting Documents", false));

        // MMC05 - names taken from the list the Name Change screen already showed.
        documents.add(create(nameChange, "Marriage Certificate / Deed Poll", true));
        documents.add(create(nameChange, "Updated NIC / Passport", true));
        documents.add(create(nameChange, "Letter from Employer", false));

        // MMC18 - names taken from the list the Nominee Change screen already showed.
        documents.add(create(nomineeChange, "Nominee NIC / Birth Certificate Copy", true));
        documents.add(create(nomineeChange, "Nominee Declaration Form", true));
        documents.add(create(nomineeChange, "Other Supporting Documents", false));

        // MMC14 - names taken from the list the Remittance Change screen already showed.
        documents.add(create(remittanceChange, "Completed Remittance Change Form", true));
        documents.add(create(remittanceChange, "Current Payslip", true));
        documents.add(create(remittanceChange, "Other Supporting Documents", false));

        // MMC12 / MMC25 - optional, see the constant comments above.
        documents.add(create(NAME_CHANGE_APPROVAL_REPORT, "Signed Name Change Approval List", false));
        documents.add(create(NOMINEE_CHANGE_APPROVAL_REPORT, "Signed Nominee Change Approval List", false));

        requiredDocumentRepository.saveAll(documents);

        System.out.println("Seeded " + documents.size() + " profile change required documents.");
    }

    private RequiredDocument create(String applicationType, String documentName, boolean mandatory) {
        RequiredDocument document = new RequiredDocument();
        document.setApplicationType(applicationType);
        document.setDocumentName(documentName);
        document.setMandatory(mandatory);
        return document;
    }
}
