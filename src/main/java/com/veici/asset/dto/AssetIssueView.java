package com.veici.asset.dto;

import com.veici.asset.entity.AssetStatus;

public record AssetIssueView(
        Long applicationId,
        Long applicationVersion,
        Long assetVersion,
        Long applicantId,
        String assetCode,
        String assetName,
        String purpose,
        AssetStatus assetStatus
) {
}