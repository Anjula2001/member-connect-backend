package com.memberconnect.backend.repository;

import com.memberconnect.backend.enums.MemberTransferStatus;
import com.memberconnect.backend.model.MemberTransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface MemberTransferRepository
        extends JpaRepository<MemberTransferRequest, Long>,
                JpaSpecificationExecutor<MemberTransferRequest> {

    Optional<MemberTransferRequest> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);

    // member_id holds the member's business key (see MemberTransferRequest's
    // @JoinColumn referencedColumnName), so these navigate member.memberId
    boolean existsByMember_MemberIdAndStatus(String memberId, MemberTransferStatus status);

    Optional<MemberTransferRequest> findFirstByMember_MemberIdAndStatus(
            String memberId, MemberTransferStatus status);
}
