package com.memberconnect.backend.dto;

public class MemberSummaryDTO {

    private String memberId;
    private String fullName;
    private String nameWithInitials;
    private String nic;
    private String status;

    public MemberSummaryDTO() {
    }

    public MemberSummaryDTO(
            String memberId,
            String fullName,
            String nameWithInitials,
            String nic,
            String status
    ) {
        this.memberId = memberId;
        this.fullName = fullName;
        this.nameWithInitials = nameWithInitials;
        this.nic = nic;
        this.status = status;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getNameWithInitials() {
        return nameWithInitials;
    }

    public String getNic() {
        return nic;
    }

    public String getStatus() {
        return status;
    }
}