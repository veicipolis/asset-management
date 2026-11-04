package com.veici.asset.repository;

import com.veici.asset.entity.ApplicationStatus;
import com.veici.asset.entity.AssetApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.Collection;

public interface AssetApplicationRepository
        extends JpaRepository<AssetApplication, Long> {

    boolean existsByApplicantIdAndAsset_IdAndStatusIn(
            Long applicantId,
            Long assetId,
            Collection<ApplicationStatus> statuses
    );

    @EntityGraph(attributePaths = "asset")
    Page<AssetApplication> findByApplicantId(
            Long applicantId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "asset")
    Page<AssetApplication> findByDepartmentIdAndStatusAndApplicantIdNot(
            Long departmentId,
            ApplicationStatus status,
            Long applicantId,
            Pageable pageable
    );

    // 只读取资产编号，避免提前把未加锁的申请实体加载进持久化上下文。
    @Query("""
        SELECT a.asset.id
        FROM AssetApplication a
        WHERE a.id = :id
        """)
    Optional<Long> findAssetIdByApplicationId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM AssetApplication a
        WHERE a.id = :id
        """)
    Optional<AssetApplication> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "asset")
    Page<AssetApplication> findByStatus(
            ApplicationStatus status,
            Pageable pageable
    );
}