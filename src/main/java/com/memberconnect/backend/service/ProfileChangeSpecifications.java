package com.memberconnect.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.memberconnect.backend.enums.ApplicationStatus;
import com.memberconnect.backend.enums.MemberTransferStatus;
import com.memberconnect.backend.model.MemberTransferRequest;
import com.memberconnect.backend.model.ProfileChangeRequest;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds the filter predicate shared by all four profile change request tables.
 *
 * One builder can serve four different entities because they all extend
 * {@link ProfileChangeRequest}, so status, requestedDate, submissionLocation and
 * memberId are the same attribute names on every one of them. The generic bound is
 * what makes that type-safe rather than a set of string attribute names.
 *
 * The free-text search is deliberately NOT here: MMC02 searches Full Name, Name as in
 * Payroll, Name with Initials, Member Number and NIC, and four of those five live on
 * Member, which these tables have no association to. That match happens in
 * ProfileChangeSearchService once the members have been resolved.
 */
public final class ProfileChangeSpecifications {

    private ProfileChangeSpecifications() {
    }

    /**
     * The same filter for Member Transfers, which needs its own builder for two reasons:
     * MemberTransferRequest does not extend ProfileChangeRequest, and it has no
     * submissionLocation of its own - the location is the member's, reached through the
     * association.
     */
    public static Specification<MemberTransferRequest> transferFilter(
            Collection<MemberTransferStatus> statuses,
            Collection<String> locations,
            LocalDate from,
            LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            if (locations != null && !locations.isEmpty()) {
                predicates.add(root.get("member").get("submissionLocation").in(locations));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("requestedDate"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("requestedDate"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static <T extends ProfileChangeRequest> Specification<T> filter(
            Collection<ApplicationStatus> statuses,
            Collection<String> locations,
            LocalDate from,
            LocalDate to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            if (locations != null && !locations.isEmpty()) {
                predicates.add(root.get("submissionLocation").in(locations));
            }

            // Requests created before requestedDate existed have a null date. They are
            // excluded from a bounded range rather than silently included, which would
            // put undated rows inside every period the user picks.
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("requestedDate"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("requestedDate"), to));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
