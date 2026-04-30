package com.memberconnect.backend.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Member;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByMemberId(String memberId);
}
   
