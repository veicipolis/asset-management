package com.veici.asset.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "asset")
public class Asset extends VersionedEntity {

    @Setter
    @Column(name = "asset_code", nullable = false,
            unique = true, length = 50, updatable = false)
    private String assetCode;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private AssetCategory category;

    @Setter
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Setter
    @Column(name = "original_value", nullable = false,
            precision = 15, scale = 2)
    private BigDecimal originalValue;

    @Setter
    @Column(nullable = false, length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.IDLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void markInUse() {
        if (status != AssetStatus.IDLE) {
            throw new IllegalArgumentException("只有闲置资产可以发放");
        }

        status = AssetStatus.IN_USE;
    }

    public void markReturned() {
        if (status != AssetStatus.IN_USE) {
            throw new IllegalArgumentException("只有使用中的资产可以接收归还");
        }
        status = AssetStatus.IDLE;
    }
}