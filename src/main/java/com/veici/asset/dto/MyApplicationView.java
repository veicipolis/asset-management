package com.veici.asset.dto;

import com.veici.asset.entity.ApplicationStatus;

import java.time.LocalDateTime;

public record MyApplicationView(
        Long id,
        String assetCode,
        String assetName,
        String purpose,
        ApplicationStatus status,
        LocalDateTime createdAt
) {
}