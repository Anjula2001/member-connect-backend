package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.memberconnect.backend.enums.MembershipDocumentType;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;

import jakarta.persistence.criteria.Predicate;

/**
 * Filter and sort for the Member Directory and the three document print screens,
 * expressed as criteria the database evaluates.
 *
 * This replaces a findAll() that pulled the whole membership into the JVM and
 * filtered it there. The cost grew with the size of the table however narrow the
 * filter was, and no page could be sliced off it until the whole set had already
 * been loaded and mapped.
 *
 * Every predicate here reproduces one from the stream it replaces, including the
 * places where a null column fails the filter rather than passing it.
 */
public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    /**
     * @param query                 free-text over name, NIC and member number
     * @param approvedApplicationIds applications approved inside the requested board
     *                              meeting period, or null for "any meeting". An EMPTY
     *                              set is meaningful and distinct from null: a period
     *                              was given and no meeting in it approved anybody, so
     *                              nothing matches.
     * @param withoutDocument       keeps only members whose document has never been
     *                              printed
     */
    public static Specification<Member> filter(
            String query,
            Collection<MemberStatus> statuses,
            Collection<String> locations,
            String workingLocationType,
            String educationalZone,
            String educationalDistrict,
            LocalDate membershipStartFrom,
            LocalDate membershipStartTo,
            Set<Long> approvedApplicationIds,
            MembershipDocumentType withoutDocument
    ) {
        final String q = (query == null || query.isBlank())
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";

        final String wlt = blankToNull(workingLocationType, "all-types");
        final String zone = blankToNull(educationalZone, "all-zones");
        final String district = blankToNull(educationalDistrict, "all-districts");

        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), q),
                        cb.like(cb.lower(root.get("nameWithInitials")), q),
                        cb.like(cb.lower(root.get("nic")), q),
                        cb.like(cb.lower(root.get("memberId")), q)));
            }

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            // Matched exactly, as the stream did — these come from the district master,
            // so they are already in the stored casing.
            if (locations != null && !locations.isEmpty()) {
                predicates.add(root.get("submissionLocation").in(locations));
            }

            if (wlt != null) {
                predicates.add(cb.equal(cb.lower(root.get("workingLocationType")), wlt));
            }
            if (zone != null) {
                predicates.add(cb.equal(cb.lower(root.get("educationalZone")), zone));
            }
            if (district != null) {
                predicates.add(cb.equal(cb.lower(root.get("educationalDistrict")), district));
            }

            // A member with no start date fails a bounded period rather than passing it,
            // which is what the null checks in the stream did.
            if (membershipStartFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("membershipStartDate"), membershipStartFrom));
            }
            if (membershipStartTo != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("membershipStartDate"), membershipStartTo));
            }

            if (approvedApplicationIds != null) {
                // An empty set cannot become an SQL "in ()", and it does not mean "any" —
                // it means no meeting in the period approved anyone, so nothing matches.
                predicates.add(approvedApplicationIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("application").get("id").in(approvedApplicationIds));
            }

            if (withoutDocument != null) {
                predicates.add(cb.isNull(root.get(printedAtAttribute(withoutDocument))));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Mirrors MembershipDocumentService.printedAt(), as an attribute name. */
    private static String printedAtAttribute(MembershipDocumentType type) {
        return switch (type) {
            case MEMBERSHIP_CARD -> "membershipCardPrintedAt";
            case SIGNATURE_CARD -> "signatureCardPrintedAt";
            case PASSBOOK -> "passbookPrintedAt";
        };
    }

    /** Treats the screens' "all-*" sentinels and blanks alike as "no filter". */
    private static String blankToNull(String value, String sentinel) {
        if (value == null || value.isBlank() || sentinel.equalsIgnoreCase(value)) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * The sort options MR13/MR15/MR16/MR17 list, mapped onto entity attributes.
     *
     * Null placement is PostgreSQL's default — NULLS LAST ascending, NULLS FIRST
     * descending — which is what the in-memory comparator produced (nullsLast, then
     * reversed wholesale for descending). An explicit precedence is not available
     * here: Hibernate 6 rejects it on a Criteria query.
     *
     * The memberId tiebreaker is what makes paging safe. Without a total order, rows
     * tying on the sort column can come back in a different arrangement per query, so
     * one member appears on two pages while another is never shown at all.
     */
    public static Sort sort(String sortBy, String sortDirection) {
        String property = switch (sortBy == null ? "" : sortBy) {
            case "memberID" -> "memberId";
            case "status" -> "status";
            case "working-location-type" -> "workingLocationType";
            case "district" -> "submissionLocation";
            case "zone" -> "educationalZone";
            default -> "membershipStartDate";
        };

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
