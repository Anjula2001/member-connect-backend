package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByMemberId(String memberId);

    // Bulk counterpart of findByMemberId, so list screens resolve every member
    // in one round trip instead of one per row.
    List<Member> findByMemberIdIn(Collection<String> memberIds);
    Optional<Member> findByNic(String nic);
    List<Member> findAllByNicIsNotNull();
    List<Member> findByStatus(MemberStatus status);
    List<Member> findByStatusIn(List<MemberStatus> statuses);
}
