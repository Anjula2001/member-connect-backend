package com.memberconnect.backend.repository;

import java.util.Optional;

import com.memberconnect.backend.model.BasicProfileChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * JpaSpecificationExecutor backs the unified "All Member Profile Change Requests List"
 * (MMC02/06/15/19), whose Location, Status, Received-On and Type filters are all
 * multi-select and optional — too many combinations to express as derived query
 * methods. The four request types share the same filterable columns because they all
 * extend ProfileChangeRequest, so one specification builder serves all four repos.
 */
@Repository
public interface BasicProfileChangeRequestRepo
        extends JpaRepository<BasicProfileChangeRequest, Integer>,
                JpaSpecificationExecutor<BasicProfileChangeRequest> {

    /** Highest request number issued for a prefix, used to derive the next sequence. */
    Optional<BasicProfileChangeRequest> findFirstByRequestNoStartingWithOrderByRequestNoDesc(String prefix);
}
