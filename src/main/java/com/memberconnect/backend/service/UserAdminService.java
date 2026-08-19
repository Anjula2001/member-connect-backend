package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.CreateUserRequestDTO;
import com.memberconnect.backend.dto.ResetPasswordAdminDTO;
import com.memberconnect.backend.dto.UpdateUserAdminDTO;
import com.memberconnect.backend.dto.UserProfileDTO;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserAdminService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<UserProfileDTO> getAllUsers() {
        return userRepo.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public UserProfileDTO getUserById(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return toDto(user);
    }

    public UserProfileDTO createUser(CreateUserRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }

        String username = request.getUsername().trim().toLowerCase();

        if (userRepo.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username '" + username + "' is already taken");
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters long");
        }

        if (request.getRole() == null) {
            throw new RuntimeException("Role is required");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName() != null ? request.getFullName().trim() : username);
        user.setRole(request.getRole());
        user.setAssignedDistrict(request.getAssignedDistrict() != null ? request.getAssignedDistrict().trim() : null);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepo.save(user);
        return toDto(saved);
    }

    public UserProfileDTO updateUser(Long id, UpdateUserAdminDTO request) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getAssignedDistrict() != null) {
            user.setAssignedDistrict(request.getAssignedDistrict().trim());
        }

        if (request.getIsActive() != null) {
            user.setActive(request.getIsActive());
        }

        User saved = userRepo.save(user);
        return toDto(saved);
    }

    public void resetPassword(Long id, ResetPasswordAdminDTO request) {
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }

        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);
    }

    public UserProfileDTO toggleUserStatus(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setActive(!user.isActive());
        User saved = userRepo.save(user);
        return toDto(saved);
    }

    private UserProfileDTO toDto(User user) {
        return new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getProfilePictureUrl(),
                user.getAssignedDistrict(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
