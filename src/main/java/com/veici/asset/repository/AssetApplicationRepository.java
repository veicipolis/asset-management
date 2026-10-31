package com.veici.asset.repository;

import com.veici.asset.entity.ApplicationStatus;
import com.veici.asset.entity.AssetApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AssetApplicationRepository
        extends JpaRepository<AssetApplication, Long> {

    boolean existsByApplicantIdAndAsset_IdAndStatusIn(
            Long applicantId,
            Long assetId,
            Collection<ApplicationStatus> statuses
    );
}