package com.memberconnect.backend.repository;

import java.util.Optional;

import com.memberconnect.backend.model.NameChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NameChangeRequestRepo
        extends JpaRepository<NameChangeRequest, Integer>,
                JpaSpecificationExecutor<NameChangeRequest> {

    /** Highest request number issued for a prefix, used to derive the next sequence. */
    Optional<NameChangeRequest> findFirstByRequestNoStartingWithOrderByRequestNoDesc(String prefix);
}
