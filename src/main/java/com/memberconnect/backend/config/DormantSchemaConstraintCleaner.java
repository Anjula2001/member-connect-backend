package com.memberconnect.backend.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Prepares an existing database for the dormant module's move from free-text
 * status strings to {@link com.memberconnect.backend.enums.DormantApprovalListStatus}.
 *
 * Two problems, both created by {@code ddl-auto=update} only ever ADDING things:
 *
 * 1. Hibernate generates a CHECK constraint for an enum column and never widens
 *    an existing one. A database created before this change carries a constraint
 *    enumerating the old values, or none at all, and the first write of the new
 *    set fails.
 * 2. More seriously, the old code wrote four status values - CREATED,
 *    IN_PROGRESS, PROCESSED and INACTIVATED - and the enum now has two. Reading a
 *    legacy row whose status is IN_PROGRESS or INACTIVATED throws
 *    IllegalArgumentException while Hibernate deserialises it, which means
 *    getAllApprovalLists() returns 500 for everyone until the row is fixed.
 *    Nothing in the application can repair that, because every JPA path to the
 *    row has to read the bad value first. Hence native SQL.
 *
 * The mapping: INACTIVATED becomes PROCESSED, because an inactivated list is a
 * processed list whose members were approved. IN_PROGRESS also becomes PROCESSED
 * - a half-decided list is not a state the new single-transaction flow can
 * produce, and PROCESSED is the safe reading, since it is the one that refuses
 * further inactivation rather than inviting it.
 *
 * @Order(1) to run alongside TerminationSchemaConstraintCleaner and ahead of
 * every seeder, closing the window in which a seeder writes a value the stale
 * constraint still rejects.
 *
 * Residual window: CommandLineRunners run after the web server is already
 * accepting connections, so a request arriving in the first moments of a restart
 * can still hit the stale state. Closing that completely would need a Flyway
 * migration, which this project does not use.
 */
@Component
@Order(1)
public class DormantSchemaConstraintCleaner implements CommandLineRunner {

    private static final List<String> STATEMENTS = List.of(
            "ALTER TABLE dormant_approval_list DROP CONSTRAINT IF EXISTS dormant_approval_list_status_check",
            "UPDATE dormant_approval_list SET status = 'PROCESSED' "
                    + "WHERE status IN ('INACTIVATED', 'IN_PROGRESS')",
            "ALTER TABLE dormant_approval_list_member "
                    + "DROP CONSTRAINT IF EXISTS dormant_approval_list_member_previous_status_check"
    );

    private final JdbcTemplate jdbcTemplate;

    public DormantSchemaConstraintCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        for (String statement : STATEMENTS) {
            try {
                jdbcTemplate.execute(statement);
                System.out.println("Dormant schema cleanup: " + statement);
            } catch (Exception e) {
                // Never fatal. A missing table on a brand new database, or a
                // constraint that was never created, must not stop the
                // application from starting.
                System.err.println(
                        "Dormant schema cleanup failed (continuing): "
                                + statement + " -> " + e.getMessage()
                );
            }
        }
    }
}
