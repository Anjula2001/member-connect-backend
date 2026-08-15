package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberDeathRecordRepository extends JpaRepository<MemberDeathRecord, Long> {

    List<MemberDeathRecord> findByMember_MemberIdOrderByCreatedAtDesc(String memberId);

    Optional<MemberDeathRecord> findByRecordId(String recordId);

    @Query(
        value = """
            SELECT *
            FROM member_death_record
            WHERE record_id LIKE CONCAT(:prefix, '%')
            ORDER BY record_id DESC
            LIMIT 1
        """,
        nativeQuery = true
    )
    Optional<MemberDeathRecord> findLastRecordByPrefix(String prefix);
}
