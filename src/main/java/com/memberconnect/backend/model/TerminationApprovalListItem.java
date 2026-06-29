package com.memberconnect.backend.model;

import com.memberconnect.backend.enums.TerminationRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "termination_approval_list_item")
public class TerminationApprovalListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", nullable = false)
    private TerminationApprovalList list;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "termination_request_id", nullable = false)
    private TerminationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private TerminationRequestStatus previousStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TerminationApprovalList getList() {
        return list;
    }

    public void setList(TerminationApprovalList list) {
        this.list = list;
    }

    public TerminationRequest getRequest() {
        return request;
    }

    public void setRequest(TerminationRequest request) {
        this.request = request;
    }

    public TerminationRequestStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(TerminationRequestStatus previousStatus) {
        this.previousStatus = previousStatus;
    }
}
