package com.memberconnect.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import com.memberconnect.backend.enums.MemberStatus;
import com.memberconnect.backend.enums.Gender;
import com.memberconnect.backend.enums.Language;
import com.memberconnect.backend.enums.NatureOfOccupation;
import com.memberconnect.backend.enums.Identification;

@Data
public class MemberDTO {

    private String memberId;
    private String name;
    private String nic;
    private String status;
    private String memberFullName;
    private String nameWithInitials;


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

    public String getMemberFullName() {
        return memberFullName;
    }

    public String getNameWithInitials() {
        return nameWithInitials;
    }
}