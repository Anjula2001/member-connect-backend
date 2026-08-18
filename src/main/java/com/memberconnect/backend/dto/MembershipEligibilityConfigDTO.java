package com.memberconnect.backend.dto;

import lombok.Data;

@Data
public class MembershipEligibilityConfigDTO {
    private Long id;
    private Integer minimumAge;
    private Integer maximumAge;
}
