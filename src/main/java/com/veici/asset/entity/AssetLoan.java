package com.veici.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "asset_loan")
public class AssetLoan extends VersionedEntity {

    @Column(name = "application_id", nullable = false,
            unique = true, updatable = false)
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Column(name = "borrower_id", nullable = false, updatable = false)
    private Long borrowerId;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "received_by")
    private Long receivedBy;

    public static AssetLoan issue(
            AssetApplication application,
            Asset asset,
            Long issuerId) {

        Objects.requireNonNull(application, "申请不能为空");
        Objects.requireNonNull(asset, "资产不能为空");
        Objects.requireNonNull(issuerId, "发放人不能为空");

        if (application.getId() == null || asset.getId() == null) {
            throw new IllegalArgumentException("申请或资产尚未保存");
        }

        if (!Objects.equals(application.getAsset().getId(), asset.getId())) {
            throw new IllegalArgumentException("申请与资产不匹配");
        }

        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new IllegalArgumentException("只有已批准申请可以发放");
        }

        if (asset.getStatus() != AssetStatus.IDLE) {
            throw new IllegalArgumentException("资产当前不是闲置状态");
        }

        var loan = new AssetLoan();
        loan.applicationId = application.getId();
        loan.asset = asset;
        loan.borrowerId = application.getApplicantId();
        loan.issuedBy = issuerId;
        loan.issuedAt = LocalDateTime.now();
        return loan;
    }
}