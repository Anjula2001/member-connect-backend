package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.memberconnect.backend.enums.ProfileChangeType;

/**
 * Generates the human-readable Request ID the SRS requires on every profile change
 * request ("auto generated on Submit; on new requests it will display as 'NEW'").
 *
 * Generalises the numbering RetirementService already does for R-2026-001, so the
 * four profile-change types share one implementation instead of four copies:
 *
 *     PCR-2026-001   Basic Profile
 *     NCR-2026-001   Name
 *     NMR-2026-001   Nominee
 *     RCR-2026-001   Remittance
 *
 * The sequence restarts each calendar year, which is why the prefix carries the
 * year and the lookup is always scoped to it.
 *
 * The caller supplies the last request number for the prefix because the four
 * requests live in four different tables; keeping that query in each repository
 * avoids a cross-table scan here and keeps the SQL where its table is defined.
 */
@Service
public class RequestNumberGenerator {

    private static final String SEQUENCE_FORMAT = "%03d";
    private static final String FIRST_SEQUENCE = "001";

    /** The prefix for this type in the current year, e.g. "PCR-2026-". */
    public String prefixFor(ProfileChangeType type) {
        return type.getRequestPrefix() + "-" + LocalDate.now().getYear() + "-";
    }

    /**
     * The next request number for the type.
     *
     * @param type           which request type is being numbered
     * @param lastRequestNo  the highest existing request number carrying
     *                       {@link #prefixFor(ProfileChangeType)}, or empty if this is
     *                       the first of the year
     */
    public String next(ProfileChangeType type, Optional<String> lastRequestNo) {
        String prefix = prefixFor(type);

        return lastRequestNo
                .map(last -> prefix + String.format(SEQUENCE_FORMAT, nextSequence(last)))
                .orElse(prefix + FIRST_SEQUENCE);
    }

    /**
     * Reads the trailing sequence off an existing number and increments it.
     *
     * Falls back to 1 rather than throwing if the stored value is not in the expected
     * shape: a single malformed legacy row must not make it impossible to submit any
     * new request. A collision on the unique constraint is a louder, more useful
     * failure than a NumberFormatException on submit.
     */
    private int nextSequence(String lastRequestNo) {
        int separator = lastRequestNo.lastIndexOf('-');
        if (separator < 0 || separator == lastRequestNo.length() - 1) {
            return 1;
        }
        try {
            return Integer.parseInt(lastRequestNo.substring(separator + 1)) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
