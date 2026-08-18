package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MembershipEligibilityConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipEligibilityConfigRepository extends JpaRepository<MembershipEligibilityConfig, Long> {
}
