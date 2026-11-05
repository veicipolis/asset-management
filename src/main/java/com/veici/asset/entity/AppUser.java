package com.veici.asset.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser extends VersionedEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<AppRole> roles = new HashSet<>();

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static AppUser create(
            String username,
            String displayName,
            Long departmentId,
            String passwordHash) {

        if (username == null || username.isBlank()
                || displayName == null || displayName.isBlank()
                || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("用户信息不完整");
        }

        var user = new AppUser();
        user.username = username;
        user.displayName = displayName.strip();
        user.departmentId = departmentId;
        user.passwordHash = passwordHash;
        user.enabled = true;
        user.createdAt = LocalDateTime.now();

        // 不默认赋予任何角色。
        return user;
    }

    public void updateProfile(String displayName, Long departmentId) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("姓名不能为空");
        }

        String normalizedName = displayName.strip();

        if (normalizedName.length() > 100) {
            throw new IllegalArgumentException("姓名不能超过100个字符");
        }

        if (departmentId != null && departmentId <= 0) {
            throw new IllegalArgumentException("部门编号无效");
        }

        this.displayName = normalizedName;
        this.departmentId = departmentId;
    }
}