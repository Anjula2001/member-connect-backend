package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDocumentDispatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberDocumentDispatchRepository extends JpaRepository<MemberDocumentDispatch, Long> {

    Optional<MemberDocumentDispatch> findByDispatchNo(String dispatchNo);

    // Latest dispatches first — the "View Previous Dispatch Details" popup shows
    // the most recent ten by default.
    List<MemberDocumentDispatch> findAllByOrderByDispatchDateDescIdDesc();

    // Used to derive the next sequence number for the "DSP-<year>-<seq>" ID format.
    Optional<MemberDocumentDispatch> findFirstByDispatchNoStartingWithOrderByDispatchNoDesc(String prefix);
}
