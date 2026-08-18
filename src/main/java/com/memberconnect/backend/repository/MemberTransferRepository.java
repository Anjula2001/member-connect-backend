package com.memberconnect.backend.repository;

import com.memberconnect.backend.model.MemberTransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberTransferRepository extends JpaRepository<MemberTransferRequest, Long> {

    Optional<MemberTransferRequest> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);
}
