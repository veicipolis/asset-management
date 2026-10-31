package com.veici.asset.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OperationLogRepository {

    private final EntityManager entityManager;

    public void recordAssetCreated(
            Long actorId, Long assetId, String assetCode, String name) {

        entityManager.createNativeQuery("""
                INSERT INTO operation_log
                    (actor_id, log_category, action,
                     target_type, target_id, result, details)
                VALUES
                    (:actorId, 'BUSINESS', 'ASSET_CREATE',
                     'ASSET', :assetId, 'SUCCESS',
                     JSON_OBJECT('assetCode', :assetCode, 'name', :name))
                """)
                .setParameter("actorId", actorId)
                .setParameter("assetId", assetId)
                .setParameter("assetCode", assetCode)
                .setParameter("name", name)
                .executeUpdate();
    }
}