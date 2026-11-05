package com.veici.asset.dto;

import java.time.LocalDateTime;

public record MyLoanView(
        Long id,
        String assetCode,
        String assetName,
        LocalDateTime issuedAt,
        LocalDateTime returnedAt,
        boolean returnPending,
        Long returnApplicationId,
        Long returnVersion
) {
}