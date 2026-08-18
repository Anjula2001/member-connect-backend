package com.memberconnect.backend.config;

import com.memberconnect.backend.enums.Role;
import com.memberconnect.backend.model.User;
import com.memberconnect.backend.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AdminSeeder implements ApplicationRunner {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // Only seed if super admin does not exist yet
        if (!userRepo.existsByUsername("superadmin")) {
            User admin = new User();
            admin.setUsername("superadmin");
            admin.setPassword(passwordEncoder.encode("Admin@1234"));
            admin.setFullName("Super Administrator");
            admin.setRole(Role.SUPER_ADMIN);
            admin.setActive(true);
            admin.setCreatedAt(LocalDateTime.now());

            userRepo.save(admin);

            System.out.println("=================================================");
            System.out.println("  Super Admin seeded successfully!");
            System.out.println("  Username : superadmin");
            System.out.println("  Password : Admin@1234");
            System.out.println("  Please change the password after first login.");
            System.out.println("=================================================");
        }
    }
}
