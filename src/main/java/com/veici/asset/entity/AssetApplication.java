package com.veici.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Objects;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "asset_application")
public class AssetApplication extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Column(name = "applicant_id", nullable = false, updatable = false)
    private Long applicantId;

    // 保存申请时的部门，后续人员调动不改变历史申请。
    @Column(name = "department_id", nullable = false, updatable = false)
    private Long departmentId;

    @Column(nullable = false, length = 200, updatable = false)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approval_comment", length = 200)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AssetApplication pending(
            Asset asset,
            Long applicantId,
            Long departmentId,
            String purpose) {

        var application = new AssetApplication();
        application.asset = asset;
        application.applicantId = applicantId;
        application.departmentId = departmentId;
        application.purpose = purpose.strip();
        return application;
    }

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void review(AppUser reviewer, boolean approved, String comment) {
        if (reviewer == null || reviewer.getId() == null
                || !reviewer.isEnabled()) {
            throw new IllegalArgumentException("审批账号无效");
        }

        boolean hasApprovalRole = reviewer.getRoles().stream()
                .anyMatch(role -> "DEPT_APPROVER".equals(role.getCode()));

        if (!hasApprovalRole) {
            throw new IllegalArgumentException("当前账号没有部门审批权限");
        }

        if (reviewer.getDepartmentId() == null
                || !Objects.equals(departmentId, reviewer.getDepartmentId())) {
            throw new IllegalArgumentException("只能审批本部门的申请");
        }

        if (Objects.equals(applicantId, reviewer.getId())) {
            throw new IllegalArgumentException("不能审批自己提交的申请");
        }

        if (status != ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("申请已被处理，请刷新页面");
        }

        String normalizedComment = comment == null ? "" : comment.strip();

        if (normalizedComment.length() > 200) {
            throw new IllegalArgumentException("审批意见不能超过200个字符");
        }

        if (!approved && normalizedComment.isBlank()) {
            throw new IllegalArgumentException("驳回申请时必须填写原因");
        }

        status = approved
                ? ApplicationStatus.APPROVED
                : ApplicationStatus.REJECTED;

        approverId = reviewer.getId();
        approvalComment = normalizedComment.isEmpty()
                ? null : normalizedComment;
        approvedAt = LocalDateTime.now();
    }
}