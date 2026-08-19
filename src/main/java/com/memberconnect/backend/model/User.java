package com.memberconnect.backend.model;

import com.memberconnect.backend.config.RolePermissions;
import com.memberconnect.backend.enums.Permission;
import com.memberconnect.backend.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "Users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "assigned_district")
    private String assignedDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ---------- UserDetails interface ----------

    /**
     * Emits both the role ("ROLE_HEAD_OFFICE") and every fine-grained permission the
     * role carries ("G5_LIST_PROCESS"). Existing role-based checks keep working; new
     * Grade 5 endpoints authorize on the permission instead, so a right can be moved
     * between roles by editing {@link RolePermissions} alone.
     *
     * Authorities are resolved from the database on every request rather than read
     * from the JWT, so a role change takes effect immediately without re-issuing tokens.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (Permission permission : RolePermissions.forRole(role)) {
            authorities.add(new SimpleGrantedAuthority(permission.name()));
        }
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
