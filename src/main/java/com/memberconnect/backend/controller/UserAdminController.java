package com.memberconnect.backend.controller;

import com.memberconnect.backend.dto.CreateUserRequestDTO;
import com.memberconnect.backend.dto.ResetPasswordAdminDTO;
import com.memberconnect.backend.dto.UpdateUserAdminDTO;
import com.memberconnect.backend.dto.UserProfileDTO;
import com.memberconnect.backend.service.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserAdminController {

    @Autowired
    private UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<List<UserProfileDTO>> getAllUsers() {
        return ResponseEntity.ok(userAdminService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getUserById(id));
    }

    @PostMapping
    public ResponseEntity<UserProfileDTO> createUser(@RequestBody CreateUserRequestDTO request) {
        UserProfileDTO created = userAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfileDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserAdminDTO request
    ) {
        return ResponseEntity.ok(userAdminService.updateUser(id, request));
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @RequestBody ResetPasswordAdminDTO request
    ) {
        userAdminService.resetPassword(id, request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<UserProfileDTO> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.toggleUserStatus(id));
    }
}
