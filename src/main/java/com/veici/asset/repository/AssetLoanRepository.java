package com.veici.asset.repository;

import com.veici.asset.entity.AssetLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface AssetLoanRepository
        extends JpaRepository<AssetLoan, Long> {

    boolean existsByAsset_IdAndReturnedAtIsNull(Long assetId);

    boolean existsByApplicationId(Long applicationId);

    // 只查询资产主键，不提前加载领用实体。
    @Query("""
        SELECT loan.asset.id
        FROM AssetLoan loan
        WHERE loan.id = :id
        """)
    Optional<Long> findAssetIdByLoanId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT loan
        FROM AssetLoan loan
        WHERE loan.id = :id
        """)
    Optional<AssetLoan> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "asset")
    Page<AssetLoan> findByBorrowerId(Long borrowerId, Pageable pageable);
}