package com.memberconnect.backend.dto;

import com.memberconnect.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequestDTO {
    private String username;
    private String password;
    private String fullName;
    private Role role;
    private String assignedDistrict;
    private Boolean authorized;
}
