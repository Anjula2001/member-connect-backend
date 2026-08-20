package com.memberconnect.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copies rows from the old {@code dormant_approval_list_members} join table into
 * the new {@code dormant_approval_list_member} entity table.
 *
 * Why a new table rather than reusing the old one: the old join table has only
 * the two foreign key columns and a composite primary key. Pointing the new
 * entity at it and letting ddl-auto=update add the extra columns is the tempting
 * shortcut and it is a trap - update would add an {@code id} column it cannot
 * populate for existing rows, and would not drop the composite key. The first
 * read of a legacy row then fails on a null @Id. A new table is created clean
 * and the rows are copied here.
 *
 * The old table is left in place, unreferenced and harmless. Dropping it is a
 * separate decision for whoever is confident the rollback window has closed.
 *
 * Idempotent by the NOT EXISTS guard, so a restart cannot double-insert. Runs at
 * @Order(2), after DormantSchemaConstraintCleaner has normalised the status
 * values and before any seeder touches these tables.
 *
 * previous_status is set to SENT_FOR_DORMANT_APPROVAL for every copied row: that
 * is where createApprovalList put a member, and it is what the old
 * deleteApprovalList assumed when rolling one back. Recording it explicitly is
 * what lets the new code stop assuming.
 */
@Component
@Order(2)
public class DormantApprovalListEntryBackfillRunner implements CommandLineRunner {

    private static final String BACKFILL = """
            INSERT INTO dormant_approval_list_member
                (dormant_approval_list_id, member_id, member_no, previous_status)
            SELECT j.dormant_approval_list_id,
                   j.member_id,
                   m.member_id,
                   'SENT_FOR_DORMANT_APPROVAL'
            FROM dormant_approval_list_members j
            JOIN member m ON m.id = j.member_id
            WHERE NOT EXISTS (
                SELECT 1 FROM dormant_approval_list_member e
                WHERE e.dormant_approval_list_id = j.dormant_approval_list_id
                  AND e.member_id = j.member_id
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public DormantApprovalListEntryBackfillRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            int copied = jdbcTemplate.update(BACKFILL);
            if (copied > 0) {
                System.out.println(
                        "Dormant backfill: copied " + copied
                                + " approval list member(s) to dormant_approval_list_member.");
            }
        } catch (Exception e) {
            // A brand new database has no old join table, which is the normal
            // case from here on and must not stop the application starting.
            System.err.println(
                    "Dormant approval list backfill skipped (continuing): " + e.getMessage());
        }
    }
}
