package com.veici.asset.repository;

import com.veici.asset.entity.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCategoryRepository
        extends JpaRepository<AssetCategory, Long> {
}