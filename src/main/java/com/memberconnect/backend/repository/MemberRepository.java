package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository
        extends JpaRepository<Member, Long>,
                JpaSpecificationExecutor<Member> {
    Optional<Member> findByMemberId(String memberId);

    // Bulk counterpart of findByMemberId, so list screens resolve every member
    // in one round trip instead of one per row.
    List<Member> findByMemberIdIn(Collection<String> memberIds);
    Optional<Member> findByNic(String nic);
    List<Member> findAllByNicIsNotNull();
    List<Member> findByStatus(MemberStatus status);
    List<Member> findByStatusIn(List<MemberStatus> statuses);

    // Used to derive the next sequence number for the "MEM-<year>-<seq>" ID format.
    Optional<Member> findFirstByMemberIdStartingWithOrderByMemberIdDesc(String prefix);

    /**
     * Counted in the database rather than by loading rows.
     *
     * The dashboard needs a number, not the records behind it; reading the whole
     * table to call .length on it is what this replaces.
     */
    long countBySubmissionLocationIn(Collection<String> locations);
}
