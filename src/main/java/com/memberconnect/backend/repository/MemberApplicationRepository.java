package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.Member_Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberApplicationRepository
        extends JpaRepository<Member_Application, Long>,
                JpaSpecificationExecutor<Member_Application> {

    /**
     * Projection for the registration list's "select all", which needs the identifiers
     * of every matching row and nothing else. Reading the one column keeps that query
     * cheap enough to run against the whole result set.
     */
    interface ApplicationIdView {
        String getApplicationID();
    }

    Optional<Member_Application> findByApplicationID(String applicationID);

    Optional<Member_Application> findByNicNumber(String nicNumber);

    List<Member_Application> findAllByNicNumberIsNotNull();

    // Used to derive the next sequence number for the "APP-<year>-<seq>" ID format.
    Optional<Member_Application> findFirstByApplicationIDStartingWithOrderByApplicationIDDesc(String prefix);

    /**
     * Counted in the database rather than by loading rows.
     *
     * The dashboard needs a number, not the records behind it; reading the whole
     * table to call .length on it is what this replaces.
     */
    long countBySubmissionLocationIn(Collection<String> locations);
}