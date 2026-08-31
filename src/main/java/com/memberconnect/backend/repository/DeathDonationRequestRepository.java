package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeathDonationRequestRepository extends JpaRepository<DeathDonationRequest, Long> {

    // The member is LAZY, and every one of these callers reads member fields for
    // each row - the list screen, the relatives lookup and the DTO mapper all do.
    // Join-fetching keeps that to a single query instead of one per request.

    @EntityGraph(attributePaths = "member")
    List<DeathDonationRequest> findByMember_MemberIdOrderByRequestedDateDesc(String memberId);

    @EntityGraph(attributePaths = "member")
    List<DeathDonationRequest> findAllByOrderByRequestedDateDesc();

    Optional<DeathDonationRequest> findByRequestNo(String requestNo);

    @EntityGraph(attributePaths = "member")
    List<DeathDonationRequest> findByDeathCertificateNumberIgnoreCase(String deathCertificateNumber);

    /**
     * For the one-time submission-location backfill, which runs from a
     * CommandLineRunner - outside any transaction, so a LAZY member would be an
     * uninitialised proxy with no session behind it. The member has to come back
     * with the request or it cannot be read at all.
     */
    @EntityGraph(attributePaths = "member")
    @Query("SELECT r FROM DeathDonationRequest r ORDER BY r.id")
    List<DeathDonationRequest> findAllWithMember();

    @Query(
        value = """
            SELECT *
            FROM death_donation_request
            WHERE request_id LIKE CONCAT(:prefix, '%')
            ORDER BY request_id DESC
            LIMIT 1
        """,
        nativeQuery = true
    )
    Optional<DeathDonationRequest> findLastRequestByPrefix(String prefix);
}
