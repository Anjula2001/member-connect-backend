package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberDeathRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberDeathRecordRepository extends JpaRepository<MemberDeathRecord, Long> {

    List<MemberDeathRecord> findByMember_MemberIdOrderByCreatedAtDesc(String memberId);

    // The member is LAZY, and the list screen reads member fields for every row.
    // Join-fetching it here keeps that to a single query instead of one per record.
    // The explicit ORDER BY matters: the list sort is stable, so this is what
    // decides the order of rows that tie on the sort key.
    @EntityGraph(attributePaths = "member")
    @Query("SELECT r FROM MemberDeathRecord r ORDER BY r.id")
    List<MemberDeathRecord> findAllWithMember();

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
