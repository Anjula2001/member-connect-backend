package com.memberconnect.backend.service;

import com.memberconnect.backend.dto.ChangePasswordRequestDTO;
import com.memberconnect.backend.dto.UpdateProfileRequestDTO;
import com.memberconnect.backend.dto.UserProfileDTO;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserProfileService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserProfileDTO getProfile(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return toDto(user);
    }

    public UserProfileDTO updateProfile(String username, UpdateProfileRequestDTO request) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl().trim());
        }

        User saved = userRepo.save(user);
        return toDto(saved);
    }

    public void changePassword(String username, ChangePasswordRequestDTO request) {
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
            throw new RuntimeException("Current password is required");
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new RuntimeException("New password must be at least 6 characters long");
        }

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);
    }

    private UserProfileDTO toDto(User user) {
        return new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getProfilePictureUrl(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
