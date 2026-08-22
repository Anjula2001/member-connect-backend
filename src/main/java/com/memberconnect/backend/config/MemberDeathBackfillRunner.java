package com.memberconnect.backend.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.memberconnect.backend.model.Member;
import com.memberconnect.backend.model.MemberDeathDocument;
import com.memberconnect.backend.model.MemberDeathRecord;
import com.memberconnect.backend.model.RequiredDocument;
import com.memberconnect.backend.model.UploadedDocument;
import com.memberconnect.backend.repository.MemberDeathDocumentRepository;
import com.memberconnect.backend.repository.MemberDeathRecordRepository;
import com.memberconnect.backend.repository.RequiredDocumentRepository;
import com.memberconnect.backend.repository.UploadedDocumentRepository;

/**
 * One-time backfills for Record Member Death, both of which exist because the
 * feature gained new machinery after records already existed.
 *
 * 1. submission_location. Death search scopes District Office users to their own
 *    assigned district and treats a null location as invisible, so without this
 *    every historic record would disappear from the district that raised it.
 *
 * 2. Uploaded documents. Death used to keep its files in its own
 *    member_death_document table; it now uses the shared Supporting Documents
 *    master like termination does. Submission checks the master, so a record
 *    whose death certificate only exists in the old table would otherwise look
 *    as though it had no documents at all.
 *
 * Both halves are idempotent - they only ever fill gaps, never overwrite - so
 * this is a no-op on every boot after the first.
 *
 * Runs at order 6, after MemberDeathDocumentSeeder (order 3) has created the
 * required-document rows this maps onto.
 */
@Component
@Order(6)
public class MemberDeathBackfillRunner implements CommandLineRunner {

    /**
     * Legacy document type to the master document it corresponds to. The old path
     * only ever recorded these two types.
     */
    private static final Map<String, String> LEGACY_TYPE_TO_DOCUMENT_NAME = Map.of(
            "DEATH_CERTIFICATE", "Death Certificate",
            "OTHER", "Other Supporting Documents"
    );

    private final MemberDeathRecordRepository recordRepository;
    private final MemberDeathDocumentRepository deathDocumentRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;

    public MemberDeathBackfillRunner(
            MemberDeathRecordRepository recordRepository,
            MemberDeathDocumentRepository deathDocumentRepository,
            RequiredDocumentRepository requiredDocumentRepository,
            UploadedDocumentRepository uploadedDocumentRepository
    ) {
        this.recordRepository = recordRepository;
        this.deathDocumentRepository = deathDocumentRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.uploadedDocumentRepository = uploadedDocumentRepository;
    }

    @Override
    public void run(String... args) {
        List<MemberDeathRecord> records = recordRepository.findAllWithMember();
        if (records.isEmpty()) {
            return;
        }

        backfillSubmissionLocation(records);
        backfillUploadedDocuments(records);
    }

    private void backfillSubmissionLocation(List<MemberDeathRecord> records) {
        List<MemberDeathRecord> updated = records.stream()
                .filter(record -> isBlank(record.getSubmissionLocation()))
                .filter(record -> {
                    Member member = record.getMember();
                    return member != null && !isBlank(member.getSubmissionLocation());
                })
                .peek(record -> record.setSubmissionLocation(record.getMember().getSubmissionLocation()))
                .toList();

        if (updated.isEmpty()) {
            return;
        }

        recordRepository.saveAll(updated);
        System.out.println("Backfilled submission location on " + updated.size() + " member death records.");
    }

    private void backfillUploadedDocuments(List<MemberDeathRecord> records) {
        Map<String, Long> requiredDocumentIdsByName = requiredDocumentRepository
                .findByApplicationTypeIn(List.of(
                        MemberDeathDocumentSeeder.MEMBER_DEATH,
                        MemberDeathDocumentSeeder.MEMBER_DEATH_MINOR))
                .stream()
                .collect(Collectors.toMap(
                        RequiredDocument::getDocumentName,
                        RequiredDocument::getId,
                        (first, second) -> first));

        if (requiredDocumentIdsByName.isEmpty()) {
            // The seeder has not run, or an administrator removed the rows. Nothing
            // to map onto, so leave the legacy files where they are.
            return;
        }

        List<UploadedDocument> migrated = new ArrayList<>();

        for (MemberDeathRecord record : records) {
            List<MemberDeathDocument> legacyDocuments =
                    deathDocumentRepository.findByRecord_RecordId(record.getRecordId());
            if (legacyDocuments.isEmpty()) {
                continue;
            }

            // Keyed per FILE, not per document type. A record may hold several
            // files against the same type - two "Other Supporting Documents", say -
            // and a per-type guard would migrate the first and silently drop the
            // rest. Since the legacy download route is gone, a dropped file would
            // be unreachable rather than merely duplicated.
            Set<String> alreadyPresent = uploadedDocumentRepository
                    .findByRequestNo(record.getRecordId())
                    .stream()
                    .map(existing -> migrationKey(existing.getRequiredDocumentId(), existing.getFileName()))
                    .collect(Collectors.toSet());

            for (MemberDeathDocument legacy : legacyDocuments) {
                String documentName = LEGACY_TYPE_TO_DOCUMENT_NAME.get(
                        legacy.getDocumentType() != null ? legacy.getDocumentType().toUpperCase() : "");
                if (documentName == null) {
                    continue;
                }

                Long requiredDocumentId = requiredDocumentIdsByName.get(documentName);
                if (requiredDocumentId == null) {
                    continue;
                }

                String key = migrationKey(requiredDocumentId, legacy.getFileName());

                // Already migrated on an earlier boot, or uploaded through the new
                // path since. Either way there is nothing to add.
                if (!alreadyPresent.add(key)) {
                    continue;
                }

                UploadedDocument document = new UploadedDocument();
                document.setRequestNo(record.getRecordId());
                document.setRequiredDocumentId(requiredDocumentId);
                document.setFileName(legacy.getFileName());
                document.setFileType(legacy.getFileType());
                document.setFilePath(legacy.getFilePath());
                document.setUploadedAt(legacy.getUploadedAt() != null
                        ? legacy.getUploadedAt()
                        : LocalDateTime.now());

                migrated.add(document);
            }
        }

        if (migrated.isEmpty()) {
            return;
        }

        uploadedDocumentRepository.saveAll(migrated);
        System.out.println("Backfilled " + migrated.size() + " member death uploaded documents.");
    }

    private String migrationKey(Long requiredDocumentId, String fileName) {
        return requiredDocumentId + "|" + (fileName != null ? fileName : "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
