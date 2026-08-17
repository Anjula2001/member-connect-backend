package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByMemberId(String memberId);
    Optional<Member> findByNic(String nic);
    List<Member> findAllByNicIsNotNull();
    List<Member> findByStatus(MemberStatus status);
    List<Member> findByStatusIn(List<MemberStatus> statuses);

    // Used to derive the next sequence number for the "MEM-<year>-<seq>" ID format.
    Optional<Member> findFirstByMemberIdStartingWithOrderByMemberIdDesc(String prefix);
}
