package com.memberconnect.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One dispatch batch: the set of members whose membership documentation was put
 * in the post together (MR18).
 *
 * Kept as a batch rather than a per-member flag because the spec's "View Previous
 * Dispatch Details" screen lists dispatches (date + member count) and downloads a
 * Dispatch Report per batch. Member.documentsDispatchedAt is maintained alongside
 * this so the "Non-Dispatched Members" filter stays a simple column check.
 */
@Getter
@Setter
@Entity
@Table(name = "member_document_dispatch")
public class MemberDocumentDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispatch_no", unique = true, nullable = false)
    private String dispatchNo;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Column(name = "dispatched_by")
    private String dispatchedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "member_document_dispatch_members",
            joinColumns = @JoinColumn(name = "dispatch_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private List<Member> members = new ArrayList<>();
}
