package com.memberconnect.backend.repository;

import java.util.Optional;

import com.memberconnect.backend.model.NommineChangeRequests;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NominneChangeRequestRepo
        extends JpaRepository<NommineChangeRequests, Integer>,
                JpaSpecificationExecutor<NommineChangeRequests> {

    /** Highest request number issued for a prefix, used to derive the next sequence. */
    Optional<NommineChangeRequests> findFirstByRequestNoStartingWithOrderByRequestNoDesc(String prefix);
}
