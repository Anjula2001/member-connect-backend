package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.ChangePasswordRequestDTO;
import com.memberconnect.backend.dto.UpdateProfileRequestDTO;
import com.memberconnect.backend.dto.UserProfileDTO;
import com.memberconnect.backend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "http://localhost:3000")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(userProfileService.getProfile(username));
    }

    @PutMapping
    public ResponseEntity<UserProfileDTO> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequestDTO request
    ) {
        String username = authentication.getName();
        return ResponseEntity.ok(userProfileService.updateProfile(username, request));
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequestDTO request
    ) {
        String username = authentication.getName();
        userProfileService.changePassword(username, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
