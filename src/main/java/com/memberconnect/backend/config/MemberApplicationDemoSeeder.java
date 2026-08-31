package com.memberconnect.backend.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Identification;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.model.BoardApprovalList;
import com.memberconnect.backend.model.BoardMeeting;
import com.memberconnect.backend.model.Member_Application;
import com.memberconnect.backend.repository.BoardApprovalListRepository;
import com.memberconnect.backend.repository.BoardmeetingRepository;
import com.memberconnect.backend.repository.MemberApplicationRepository;

/**
 * Mock member applications for the New Member Registration screens.
 *
 * <h2>Why this exists alongside MemberDemoSeeder</h2>
 *
 * MemberDemoSeeder creates applications that have ALREADY become members, so every
 * one of them is ApplicationStatus.APPROVED and, by spec, excluded from the New
 * Member Registration List. That is correct for what it is for - giving Loans,
 * Scholarships and Termination members to work against - but it leaves the
 * registration and board-approval screens with nothing in them. This seeder fills
 * that gap with applications still moving through the pipeline.
 *
 * <h2>Where the data comes from</h2>
 *
 * resources/db/member_application_seed.csv, one row per application, column names
 * matching the member_application table. Keeping the data in a CSV rather than in
 * Java literals means a contributor can add or edit a case without touching code or
 * recompiling to reshape a demo.
 *
 * <h2>Why the IDs are not APP-2026-nnn</h2>
 *
 * The CSV carries real-format ids, which are deliberately NOT used. Two reasons:
 *
 * 1. MemberApplicationService.generateApplicationId() takes the highest existing
 *    "APP-{year}-" id and adds one. Seeding APP-2026-001..015 would silently push
 *    every genuine application created through the UI to start at 016, and demo
 *    rows would be indistinguishable from real ones ever after.
 *
 * 2. The APP-DEMO- prefix is what resources/db/undo-demo-seed.sql targets, so
 *    these rows are removable by the purge script that already exists. The extra
 *    REG segment keeps them clear of MemberDemoSeeder's APP-DEMO-001..030.
 *
 * <h2>Off by default</h2>
 *
 * Enable with {@code member-application.seed.demo=true} (or the environment
 * variable MEMBER_APPLICATION_SEED_DEMO=true) for a single run, then turn it off.
 * The sentinel below makes a repeat run harmless even if it is left on.
 */
@Component
@Order(5)
public class MemberApplicationDemoSeeder implements CommandLineRunner {

    private static final String RESOURCE = "db/member_application_seed.csv";

    /** Rows created here are identifiable - and removable - by this prefix. */
    private static final String ID_PREFIX = "APP-DEMO-REG-";

    /** The board meeting and list the ADDED_TO_BOARD_APPROVAL_LIST rows belong to. */
    private static final String BOARD_MEETING_ID = "BM-DEMO-REG";
    private static final String BOARD_LIST_ID = "BAL-DEMO-REG";

    private final MemberApplicationRepository applicationRepository;
    private final BoardmeetingRepository boardMeetingRepository;
    private final BoardApprovalListRepository boardApprovalListRepository;
    private final boolean enabled;

    public MemberApplicationDemoSeeder(
            MemberApplicationRepository applicationRepository,
            BoardmeetingRepository boardMeetingRepository,
            BoardApprovalListRepository boardApprovalListRepository,
            @Value("${member-application.seed.demo:false}") boolean enabled) {
        this.applicationRepository = applicationRepository;
        this.boardMeetingRepository = boardMeetingRepository;
        this.boardApprovalListRepository = boardApprovalListRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Says so either way. A seeder that is silent when switched off is
        // indistinguishable from one that never ran.
        System.out.println("Member application demo seeder: enabled=" + enabled);

        if (!enabled) {
            return;
        }

        if (applicationRepository
                .findFirstByApplicationIDStartingWithOrderByApplicationIDDesc(ID_PREFIX)
                .isPresent()) {
            System.out.println("Member application demo seeder: " + ID_PREFIX
                    + " rows already present - skipping.");
            return;
        }

        List<Map<String, String>> rows;
        try {
            rows = readSeedFile();
        } catch (IOException e) {
            // A missing or unreadable seed file must not stop the application from
            // starting - this is demo data, not a prerequisite for running.
            System.out.println("Member application demo seeder: could not read "
                    + RESOURCE + " - " + e.getMessage());
            return;
        }

        if (rows.isEmpty()) {
            System.out.println("Member application demo seeder: " + RESOURCE + " has no rows.");
            return;
        }

        Map<ApplicationStatus, Integer> counts = new EnumMap<>(ApplicationStatus.class);
        List<Member_Application> boardHeld = new ArrayList<>();
        int index = 0;

        for (Map<String, String> row : rows) {
            index++;
            Member_Application application =
                    toApplication(row, ID_PREFIX + String.format("%03d", index));
            Member_Application persisted = applicationRepository.save(application);
            counts.merge(persisted.getStatus(), 1, Integer::sum);
            if (persisted.getStatus() == ApplicationStatus.ADDED_TO_BOARD_APPROVAL_LIST) {
                boardHeld.add(persisted);
            }
        }

        attachBoardApprovalList(boardHeld);

        System.out.println("=================================================");
        System.out.println("  Mock member applications seeded: " + rows.size());
        System.out.println("  IDs: " + ID_PREFIX + "001 .. " + ID_PREFIX
                + String.format("%03d", rows.size()));
        counts.forEach((status, count) -> System.out.println("    " + status + ": " + count));
        if (!boardHeld.isEmpty()) {
            System.out.println("  Board Approval List " + BOARD_LIST_ID + " holds "
                    + boardHeld.size() + " of them (meeting " + BOARD_MEETING_ID + ")");
        }
        System.out.println("  Remove with: backend/src/main/resources/db/undo-demo-seed.sql");
        System.out.println("=================================================");
    }

    /**
     * Gives the ADDED_TO_BOARD_APPROVAL_LIST rows a real Board Approval List to sit on.
     *
     * Without it they are exactly the inconsistency ApplicationStatusPolicy now refuses
     * to create by hand: an application whose status says the board holds it, belonging
     * to no list. It would read as "Added to Board Approval List" on the New Member
     * Registration List while the Board Approvals screen had nothing to open, and MR08
     * could never roll it back because there would be no list to delete.
     */
    private void attachBoardApprovalList(List<Member_Application> applications) {
        if (applications.isEmpty()) {
            return;
        }

        BoardMeeting meeting = boardMeetingRepository.findByBoardMeetingId(BOARD_MEETING_ID)
                .orElseGet(() -> {
                    BoardMeeting created = new BoardMeeting();
                    created.setBoardMeetingId(BOARD_MEETING_ID);
                    // Two weeks out: a list awaiting a meeting that has not happened yet is
                    // the state MR09 (print) and MR10 (process) are demonstrated from.
                    created.setScheduledDate(LocalDate.now().plusWeeks(2));
                    return boardMeetingRepository.save(created);
                });

        BoardApprovalList list = boardApprovalListRepository.findByListId(BOARD_LIST_ID)
                .orElseGet(BoardApprovalList::new);
        list.setListId(BOARD_LIST_ID);
        list.setBoardMeetingId(meeting.getId());
        list.setBoardMeetingDate(meeting.getScheduledDate());
        list.setStatus("CREATED");
        list.setCreatedAt(LocalDateTime.now());
        list.getApplications().clear();
        list.getApplications().addAll(applications);
        boardApprovalListRepository.save(list);

        // MR06 records what each application was before the list claimed it so MR08 can
        // put it back. These rows never passed through MR06, so state it here instead -
        // otherwise deleting this list would roll them to the fallback rather than to
        // the Submitted for Approval they would genuinely have come from.
        for (Member_Application application : applications) {
            application.setStatusBeforeBoardList(ApplicationStatus.SUBMITTED_FOR_APPROVAL);
            applicationRepository.save(application);
        }
    }

    private Member_Application toApplication(Map<String, String> row, String applicationId) {
        Member_Application a = new Member_Application();

        a.setApplicationID(applicationId);
        a.setStatus(enumValue(ApplicationStatus.class, row.get("status"), ApplicationStatus.NEW));
        a.setApplicationDate(text(row.get("application_date")));
        a.setSubmissionLocation(text(row.get("submission_location")));

        a.setTitle(text(row.get("title")));
        a.setFullName(text(row.get("full_name")));
        a.setNameAsInPayroll(text(row.get("name_as_in_payroll")));
        a.setNameWithInitials(text(row.get("name_with_initials")));
        a.setNicNumber(text(row.get("nic_number")));
        a.setDateOfBirth(date(row.get("date_of_birth")));
        a.setGender(enumValue(Gender.class, row.get("gender"), null));
        a.setPreferredLanguage(enumValue(Language.class, row.get("preferred_language"), null));
        a.setPermanentPrivateAddress(text(row.get("permanent_private_address")));

        a.setWorkingLocationType(text(row.get("working_location_type")));
        a.setDesignation(text(row.get("designation")));
        a.setNatureOfOccupation(
                enumValue(NatureOfOccupation.class, row.get("nature_of_occupation"), null));
        a.setEducationalDistrict(text(row.get("educational_district")));
        a.setEducationalZone(text(row.get("educational_zone")));
        a.setWorkingLocation(text(row.get("working_location")));
        a.setWorkingLocationAddress(text(row.get("working_location_address")));
        a.setComputerNoInPayslip(text(row.get("computer_no_in_payslip")));
        a.setSalaryPayingOffice(text(row.get("salary_paying_office")));

        a.setOfficeTelephone(text(row.get("office_telephone")));
        a.setPrivateTelephone(text(row.get("private_telephone")));
        a.setMobileNumber(text(row.get("mobile_number")));
        a.setEmailAddress(text(row.get("email_address")));

        a.setShareAccountAmount(amount(row.get("share_account_amount")));
        a.setSpecialDepositAmount(amount(row.get("special_deposit_amount")));
        a.setFixedDepositAmount(amount(row.get("fixed_deposit_amount")));
        a.setScholarshipDeathDonationPensionAmount(
                amount(row.get("scholarship_death_donation_pension_amount")));

        a.setNomineeFullName(text(row.get("nominee_full_name")));
        a.setNomineeRelationship(text(row.get("nominee_relationship")));
        a.setNomineeAddress(text(row.get("nominee_address")));

        a.setIdentification(
                enumValue(Identification.class, row.get("identification"), Identification.NIC));
        a.setIdentificationNumber(text(row.get("identification_number")));
        a.setIdentificationDetails(text(row.get("identification_details")));

        a.setRejoinFlag(Boolean.parseBoolean(String.valueOf(row.get("rejoin_flag"))));
        a.setBoardDecisionReason(text(row.get("board_decision_reason")));

        return a;
    }

    private List<Map<String, String>> readSeedFile() throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();

        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            List<String> headers = splitCsvLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                rows.add(row);
            }
        }

        return rows;
    }

    /**
     * Minimal RFC 4180 field splitter.
     *
     * Addresses in the seed file carry commas inside double quotes - "No 45, Temple
     * Road, Homagama" - so a plain String.split(",") would tear one address into
     * three columns and shift every field after it out of alignment. Pulling in a
     * CSV dependency for a single file this size is not worth it.
     */
    private static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // A doubled quote inside a quoted field is one literal quote.
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        fields.add(current.toString().trim());
        return fields;
    }

    /** Blank CSV cells become null rather than empty strings. */
    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate date(String value) {
        String cleaned = text(value);
        return cleaned == null ? null : LocalDate.parse(cleaned);
    }

    private static BigDecimal amount(String value) {
        String cleaned = text(value);
        return cleaned == null ? null : new BigDecimal(cleaned);
    }

    /**
     * Tolerant enum lookup. A typo in one cell should cost that one field, not the
     * whole seed run, so an unrecognised value falls back rather than throwing.
     */
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        String cleaned = text(value);
        if (cleaned == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, cleaned);
        } catch (IllegalArgumentException e) {
            System.out.println("Member application demo seeder: unrecognised "
                    + type.getSimpleName() + " value '" + cleaned + "' - using " + fallback);
            return fallback;
        }
    }
}
