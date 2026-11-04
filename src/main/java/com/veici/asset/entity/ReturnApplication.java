package com.veici.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "return_application")
public class ReturnApplication extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private AssetLoan loan;

    @Column(name = "applicant_id", nullable = false, updatable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public static ReturnApplication pending(AssetLoan loan) {
        if (loan == null || loan.getId() == null) {
            throw new IllegalArgumentException("领用记录不存在");
        }

        if (loan.getReturnedAt() != null) {
            throw new IllegalArgumentException("该领用记录已经归还");
        }

        var application = new ReturnApplication();
        application.loan = loan;
        application.applicantId = loan.getBorrowerId();
        return application;
    }

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void complete(LocalDateTime receivedAt) {
        if (status != ReturnStatus.PENDING) {
            throw new IllegalArgumentException("只有待接收的归还申请可以完成");
        }

        if (receivedAt == null
                || createdAt == null
                || receivedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("接收时间不能早于申请时间");
        }

        status = ReturnStatus.COMPLETED;
        completedAt = receivedAt;
    }
}