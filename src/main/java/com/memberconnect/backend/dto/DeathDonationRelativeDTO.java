package com.memberconnect.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeathDonationRelativeDTO {
    private Long id;
    private String relativeMemberId;
    private String relationshipToDeceased;
    private boolean autoPopulated;
    private String relativeMemberName;
}
