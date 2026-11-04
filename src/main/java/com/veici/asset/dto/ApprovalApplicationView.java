package com.veici.asset.dto;

import com.veici.asset.entity.ApplicationStatus;

import java.time.LocalDateTime;

public record ApprovalApplicationView(
        Long id,
        Long version,
        Long applicantId,
        String assetCode,
        String assetName,
        String purpose,
        ApplicationStatus status,
        LocalDateTime createdAt
) {
}