package com.veici.asset.repository;

import com.veici.asset.entity.AssetLoan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetLoanRepository
        extends JpaRepository<AssetLoan, Long> {

    boolean existsByAsset_IdAndReturnedAtIsNull(Long assetId);

    boolean existsByApplicationId(Long applicationId);
}