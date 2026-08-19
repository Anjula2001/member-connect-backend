package com.memberconnect.backend.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Drops the Postgres CHECK constraints Hibernate generates for the enum columns the
 * termination module writes to.
 *
 * Why this is needed at all: the schema is managed by {@code ddl-auto=update}, which
 * only ever ADDS tables and columns. It never widens an existing CHECK constraint. So
 * the moment a new value is added to {@link com.memberconnect.backend.enums.MemberStatus}
 * or {@link com.memberconnect.backend.enums.TerminationRequestStatus}, every existing
 * database still carries a constraint enumerating only the OLD values, and the first
 * write of the new value fails. Adding MemberStatus.TERMINATION_APPROVED (MMT09) is
 * exactly that situation.
 *
 * Why it is @Order(1): MemberConnectBackendApplication already drops
 * member_status_check, but it does so from a CommandLineRunner with no @Order, which
 * Spring therefore sorts at LOWEST_PRECEDENCE - after every @Order-ed runner,
 * including the seeders. Running the drop first removes the window in which a seeder
 * could write a status value the constraint still rejects. Both drops use
 * IF EXISTS, so the two runners overlapping on member_status_check is harmless.
 *
 * The application class is deliberately left alone: it also drops the death-donation
 * and member-death constraints, which belong to other modules.
 *
 * Residual window: CommandLineRunners run after the web server is already accepting
 * connections, so a request arriving in the first moments of a restart can still hit
 * the stale constraint. Closing that completely would need a Flyway migration, which
 * this project does not use.
 */
@Component
@Order(1)
public class TerminationSchemaConstraintCleaner implements CommandLineRunner {

    private static final List<String> DROP_STATEMENTS = List.of(
            "ALTER TABLE member DROP CONSTRAINT IF EXISTS member_status_check",
            "ALTER TABLE termination_request DROP CONSTRAINT IF EXISTS termination_request_status_check"
    );

    private final JdbcTemplate jdbcTemplate;

    public TerminationSchemaConstraintCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        for (String statement : DROP_STATEMENTS) {
            try {
                jdbcTemplate.execute(statement);
                System.out.println("Termination schema cleanup: " + statement);
            } catch (Exception e) {
                // Never fatal. A missing table on a brand new database, or a
                // constraint that was never created, must not stop the application
                // from starting.
                System.err.println(
                        "Termination schema cleanup failed (continuing): "
                                + statement + " -> " + e.getMessage()
                );
            }
        }
    }
}
