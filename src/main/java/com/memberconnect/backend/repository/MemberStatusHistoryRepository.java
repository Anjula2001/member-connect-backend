package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberStatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MemberStatusHistoryRepository extends JpaRepository<MemberStatusHistory, Long> {

    /**
     * The member's status changes that had taken effect by the given date, most recent
     * first - so the first row is the status the member held on that date.
     *
     * recordedAt and id break ties, because two changes can share one effective date
     * (a request reopened and re-approved the same day) and the later one is the one
     * that stood.
     *
     * Returns a list with a Pageable rather than an Optional so callers ask for the one
     * row they need; a derived query name for this ordering would be unreadable.
     */
    @Query("""
            select h from MemberStatusHistory h
            where h.member.memberId = :memberId
              and h.effectiveDate <= :date
            order by h.effectiveDate desc, h.recordedAt desc, h.id desc
            """)
    List<MemberStatusHistory> findStatusAsAt(
            @Param("memberId") String memberId,
            @Param("date") LocalDate date,
            Pageable pageable);

    List<MemberStatusHistory> findByMember_MemberIdOrderByEffectiveDateAscRecordedAtAsc(String memberId);
}
