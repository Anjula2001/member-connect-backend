package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberTermination;
import com.memberconnect.backend.enums.TerminationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberTerminationRepository extends JpaRepository<MemberTermination, Long> {

    // Find termination by termination ID
    Optional<MemberTermination> findByTerminationId(String terminationId);

    // Find all terminations for a specific member
    List<MemberTermination> findByMemberId(Long memberId);

    // Find all terminations by status
    List<MemberTermination> findByTerminationStatus(TerminationStatus status);

    // Find all pending terminations
    List<MemberTermination> findByTerminationStatusOrderByRequestedDateDesc(TerminationStatus status);

    // Check if member has active termination
    Optional<MemberTermination> findByMemberIdAndTerminationStatus(Long memberId, TerminationStatus status);
}
