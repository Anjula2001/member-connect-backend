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

    // The maintenance view, for the Termination Request Master screen. Deliberately
    // NOT filtered on active: an administrator has to see a retired reason in order
    // to bring it back, which is the only way back for one - nothing deletes here.
    List<TerminationReason> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<TerminationReason> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);
}
