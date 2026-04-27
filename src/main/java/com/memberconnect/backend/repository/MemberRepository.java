package com.memberconnect.backend.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.memberconnect.backend.model.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
   
