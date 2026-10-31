package com.veici.asset.repository;

import com.veici.asset.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.veici.asset.entity.AssetStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Asset> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Asset> findByAssetCode(String assetCode);

    @EntityGraph(attributePaths = "category")
    @Query("""
        SELECT a FROM Asset a
        WHERE (:assetCode IS NULL OR a.assetCode = :assetCode)
          AND (:name IS NULL OR LOCATE(:name, a.name) > 0)
          AND (:categoryId IS NULL OR a.category.id = :categoryId)
          AND (:status IS NULL OR a.status = :status)
        """)
    Page<Asset> search(
            @Param("assetCode") String assetCode,
            @Param("name") String name,
            @Param("categoryId") Long categoryId,
            @Param("status") AssetStatus status,
            Pageable pageable
    );

    boolean existsByAssetCode(String assetCode);
}