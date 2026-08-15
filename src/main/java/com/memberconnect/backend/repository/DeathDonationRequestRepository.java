package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.DeathDonationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeathDonationRequestRepository extends JpaRepository<DeathDonationRequest, Long> {

    List<DeathDonationRequest> findByMember_MemberIdOrderByRequestedDateDesc(String memberId);

    List<DeathDonationRequest> findAllByOrderByRequestedDateDesc();

    Optional<DeathDonationRequest> findByRequestNo(String requestNo);

    List<DeathDonationRequest> findByDeathCertificateNumberIgnoreCase(String deathCertificateNumber);

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
