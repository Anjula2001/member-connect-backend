package com.memberconnect.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.TerminationReason;

public interface TerminationReasonRepository extends JpaRepository<TerminationReason, Long> {

    // Selectable options for the "Termination due to" dropdown. Retired reasons
    // are excluded here but stay resolvable through findById, which is what lets
    // an existing request keep a reason that has since been deactivated.
    List<TerminationReason> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<TerminationReason> findByCode(String code);
}
