package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberDeathRecordRepository extends JpaRepository<MemberDeathRecord, Long> {
}
