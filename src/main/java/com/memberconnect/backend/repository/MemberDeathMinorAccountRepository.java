package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathMinorAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberDeathMinorAccountRepository extends JpaRepository<MemberDeathMinorAccount, Long> {
}
