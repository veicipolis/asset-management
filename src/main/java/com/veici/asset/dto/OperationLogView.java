package com.veici.asset.dto;

import java.time.LocalDateTime;

public record OperationLogView(
        Long id,
        String operator,
        LocalDateTime createdAt,
        String action,
        String targetType,
        Long targetId,
        String result,
        String details
) {
}