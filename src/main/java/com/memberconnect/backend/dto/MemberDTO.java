package com.memberconnect.backend.dto;

public class MemberDTO {

    private String memberId;
    private String name;
    private String nic;
    private String status;

    public MemberDTO() {
    }

    public MemberDTO(String memberId, String name, String nic, String status) {
        this.memberId = memberId;
        this.name = name;
        this.nic = nic;
        this.status = status;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public String getNic() {
        return nic;
    }

    public String getStatus() {
        return status;
    }
}