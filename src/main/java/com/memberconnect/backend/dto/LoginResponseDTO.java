package com.memberconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String username;
    private String fullName;
    private String role;
    private String profilePictureUrl;
    private String assignedDistrict;
}
