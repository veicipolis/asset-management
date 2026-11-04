package com.veici.asset.dto;

import java.time.LocalDateTime;

public record ReturnReceiveView(
        Long id,
        Long version,
        Long loanId,
        Long applicantId,
        String assetCode,
        String assetName,
        LocalDateTime createdAt
) {
}