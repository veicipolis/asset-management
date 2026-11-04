package com.veici.asset.dto;

public record UserAdminView(
        Long id,
        Long version,
        String username,
        String displayName,
        Long departmentId,
        boolean enabled,
        String roles
) {
}