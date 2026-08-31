package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.model.Member_Application;

import jakarta.persistence.criteria.Predicate;

/**
 * Filter and sort for the New Member Registration List, expressed as criteria the
 * database evaluates.
 *
 * This replaces a findAll() that pulled every application row into the JVM and
 * filtered/sorted it there. That approach could not be paged usefully — the cost was
 * already paid by the time a page was sliced off — so the whole predicate had to move
 * into SQL before pagination was worth anything.
 *
 * The statuses selectable for a board approval list are the two the screen lets an
 * operator tick, kept here so the row query and the "select all" count cannot drift
 * apart.
 */
public final class MemberApplicationSpecifications {

    /** Rows the registration list lets an operator tick for a board approval list. */
    public static final Set<ApplicationStatus> SELECTABLE_STATUSES =
            Set.of(ApplicationStatus.SUBMITTED_FOR_APPROVAL, ApplicationStatus.REJECTED);

    private MemberApplicationSpecifications() {
    }

    /**
     * @param query     free-text over the applicant's names and NIC; null/blank means no
     *                  keyword filter
     * @param statuses  null/empty means every status the screen shows
     * @param locations District Office branches the application was submitted at,
     *                  matched case-insensitively
     */
    public static Specification<Member_Application> filter(
            String query,
            Collection<ApplicationStatus> statuses,
            Collection<String> locations,
            LocalDate receivedFrom,
            LocalDate receivedTo
    ) {
        final String q = (query == null || query.isBlank())
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";

        final List<String> lowerLocations = (locations == null)
                ? List.of()
                : locations.stream()
                        .filter(loc -> loc != null && !loc.isBlank())
                        .map(loc -> loc.toLowerCase(Locale.ROOT))
                        .toList();

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Applications already converted into Members leave this screen entirely —
            // the spec scopes it to registrations not yet approved as Members.
            predicates.add(cb.or(
                    cb.isNull(root.get("status")),
                    cb.notEqual(root.get("status"), ApplicationStatus.APPROVED)));

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            if (!lowerLocations.isEmpty()) {
                predicates.add(cb.lower(root.get("submissionLocation")).in(lowerLocations));
            }

            // applicationDate is a String column holding ISO yyyy-MM-dd, so its
            // lexicographic order is its chronological order and the range can be
            // compared as text. A null or malformed date fails the comparison, which
            // matches what the in-memory filter did with an unparseable value.
            if (receivedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("applicationDate"), receivedFrom.toString()));
            }
            if (receivedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("applicationDate"), receivedTo.toString()));
            }

            if (q != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), q),
                        cb.like(cb.lower(root.get("nameAsInPayroll")), q),
                        cb.like(cb.lower(root.get("nameWithInitials")), q),
                        cb.like(cb.lower(root.get("nicNumber")), q)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Narrows a filter to the rows the "select all" checkbox covers. */
    public static Specification<Member_Application> selectableOnly() {
        return (root, criteriaQuery, cb) -> root.get("status").in(SELECTABLE_STATUSES);
    }

    /**
     * The screen's Sort By values mapped onto entity attributes.
     *
     * Null placement is left to PostgreSQL's default — NULLS LAST ascending, NULLS
     * FIRST descending — which is exactly what the in-memory comparator this replaces
     * produced (nullsLast, then reversed wholesale for descending). Asking for an
     * explicit precedence instead is not an option here: Hibernate 6 rejects it on a
     * Criteria query with "Applying Null Precedence using Criteria Queries is not yet
     * supported". None of the sortable columns is null on any current row, so the
     * question is academic until one is.
     *
     * The id tiebreaker is what makes paging safe: without a total order, rows that tie
     * on the sort column can be returned by the database in a different arrangement per
     * query, so the same record shows up on two pages while another is never shown.
     */
    public static Sort sort(String sortBy, String sortDirection) {
        String property = switch (sortBy == null ? "" : sortBy) {
            case "status" -> "status";
            case "district" -> "submissionLocation";
            case "zone" -> "educationalZone";
            default -> "applicationDate";
        };

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
