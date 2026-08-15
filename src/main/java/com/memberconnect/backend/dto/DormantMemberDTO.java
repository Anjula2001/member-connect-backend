package com.memberconnect.backend.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DormantMemberDTO {
    private Long id;
    private String memberId;
    private String fullName;
    private String nameWithInitials;
    private String nic;
    private String memberType;
    private String location;
    private LocalDate lastActivityDate;
    private LocalDate membershipDate;
    private LocalDate dormantSelectionDate;
    private String status;
    private boolean hasIndirectObligations;
}
