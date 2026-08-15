package com.memberconnect.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "death_donation_relative")
public class DeathDonationRelative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "death_donation_request_id", nullable = false)
    private DeathDonationRequest request;

    @Column(name = "request_id", nullable = false)
    private Long requestFkId;

    @Column(name = "relative_member_id", nullable = false)
    private String relativeMemberId;

    @Column(name = "member_id", nullable = false, length = 50)
    private String memberId;

    @Column(name = "relationship_to_deceased", nullable = false)
    private String relationshipToDeceased;

    @Column(name = "auto_populated", nullable = false)
    private boolean autoPopulated;

    @Column(name = "is_auto", nullable = false)
    private boolean isAuto;

    @PrePersist
    @PreUpdate
    private void syncLegacyColumns() {
        if (request != null && request.getId() != null) {
            requestFkId = request.getId();
        }
        if (relativeMemberId != null) {
            memberId = relativeMemberId.length() > 50
                    ? relativeMemberId.substring(0, 50)
                    : relativeMemberId;
        }
        isAuto = autoPopulated;
    }
}
