package com.memberconnect.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.TerminationMinorDisbursement;

public interface TerminationMinorDisbursementRepository
        extends JpaRepository<TerminationMinorDisbursement, Long> {

    List<TerminationMinorDisbursement> findByTerminationRequest_Id(Long terminationRequestId);

    // Loads the disbursement rows for a whole page of requests at once, so the
    // list screen never triggers the lazy collection request by request.
    List<TerminationMinorDisbursement> findByTerminationRequest_IdIn(Collection<Long> terminationRequestIds);
}
