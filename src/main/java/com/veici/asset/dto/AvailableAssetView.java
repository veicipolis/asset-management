package com.veici.asset.dto;

public record AvailableAssetView(
        Long id,
        String assetCode,
        String name,
        String categoryName,
        String location
) {
}