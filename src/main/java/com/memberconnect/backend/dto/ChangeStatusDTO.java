package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.DeathDonationStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * Small DTO used when changing the status of a request directly.
 */
@Getter
@Setter
public class ChangeStatusDTO {
    private DeathDonationStatus status;
}
