package com.veici.asset.repository;

import com.veici.asset.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OperationLogReadRepository
        extends Repository<OperationLog, Long> {

    @EntityGraph(attributePaths = "actor")
    @Query("""
        SELECT log
        FROM OperationLog log
        WHERE log.logCategory = 'BUSINESS'
          AND (:startTime IS NULL OR log.createdAt >= :startTime)
          AND (:endTime IS NULL OR log.createdAt < :endTime)
          AND (:action IS NULL OR log.action = :action)
          AND (:targetType IS NULL OR log.targetType = :targetType)
          AND (:targetId IS NULL OR log.targetId = :targetId)
        """)
    Page<OperationLog> search(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "actor")
    @Query("""
    SELECT log
    FROM OperationLog log
    WHERE log.logCategory = 'ADMIN'
      AND (:startTime IS NULL OR log.createdAt >= :startTime)
      AND (:endTime IS NULL OR log.createdAt < :endTime)
      AND (:action IS NULL OR log.action = :action)
      AND (:targetType IS NULL OR log.targetType = :targetType)
      AND (:targetId IS NULL OR log.targetId = :targetId)
    """)
    Page<OperationLog> searchAdmin(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            Pageable pageable
    );
}