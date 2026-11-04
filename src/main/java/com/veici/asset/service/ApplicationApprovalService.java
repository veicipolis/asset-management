package com.veici.asset.service;

import com.veici.asset.dto.ApplicationApprovalForm;
import com.veici.asset.dto.ApprovalDecision;
import com.veici.asset.entity.AppUser;
import com.veici.asset.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import com.veici.asset.dto.ApprovalApplicationView;
import com.veici.asset.entity.ApplicationStatus;
import com.veici.asset.entity.AssetApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class ApplicationApprovalService {

    private final AppUserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetApplicationRepository applicationRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public void review(
            @NotNull @Positive Long applicationId,
            @NotNull @Valid ApplicationApprovalForm form) {

        var reviewer = requireApprover();

        // 先查资产主键，再按“资产 → 申请”的顺序加锁。
        Long assetId = applicationRepository
                .findAssetIdByApplicationId(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "申请不存在"));

        assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        var application = applicationRepository
                .findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "申请不存在"));

        // 保存时再次校验，不能只依赖列表过滤。
        if (!Objects.equals(
                application.getDepartmentId(),
                reviewer.getDepartmentId())) {
            throw new AccessDeniedException("只能审批本部门申请");
        }

        if (Objects.equals(
                application.getApplicantId(),
                reviewer.getId())) {
            throw new AccessDeniedException("不能审批自己的申请");
        }

        if (!Objects.equals(application.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException(
                    "申请已发生变化，请重新打开审批页面");
        }

        Long beforeVersion = application.getVersion();
        boolean approved = form.getDecision() == ApprovalDecision.APPROVE;

        // 实体方法继续检查状态、权限和驳回原因。
        application.review(reviewer, approved, form.getComment());

        applicationRepository.flush();

        logRepository.recordApplicationReviewed(
                reviewer.getId(),
                application.getId(),
                approved,
                application.getApprovalComment(),
                beforeVersion,
                application.getVersion()
        );
    }

    private AppUser requireApprover() {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var reviewer = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("账号不存在"));

        boolean allowed = reviewer.isEnabled()
                && reviewer.getRoles().stream()
                .anyMatch(role -> "DEPT_APPROVER".equals(role.getCode()));

        if (!allowed || reviewer.getDepartmentId() == null) {
            throw new AccessDeniedException(
                    "当前账号没有部门审批权限或未分配部门");
        }

        return reviewer;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalApplicationView> findPending(int page) {
        var reviewer = requireApprover();

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.ASC, "createdAt", "id")
        );

        return applicationRepository
                .findByDepartmentIdAndStatusAndApplicantIdNot(
                        reviewer.getDepartmentId(),
                        ApplicationStatus.PENDING,
                        reviewer.getId(),
                        pageable
                )
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public ApprovalApplicationView getForReview(
            @NotNull @Positive Long applicationId) {

        var reviewer = requireApprover();

        var application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "申请不存在"));

        if (!Objects.equals(
                application.getDepartmentId(), reviewer.getDepartmentId())) {
            throw new AccessDeniedException("只能查看本部门审批申请");
        }

        if (Objects.equals(application.getApplicantId(), reviewer.getId())) {
            throw new AccessDeniedException("不能审批自己的申请");
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "申请已被处理，请返回待审批列表");
        }

        return toView(application);
    }

    private ApprovalApplicationView toView(AssetApplication application) {
        return new ApprovalApplicationView(
                application.getId(),
                application.getVersion(),
                application.getApplicantId(),
                application.getAsset().getAssetCode(),
                application.getAsset().getName(),
                application.getPurpose(),
                application.getStatus(),
                application.getCreatedAt()
        );
    }
}