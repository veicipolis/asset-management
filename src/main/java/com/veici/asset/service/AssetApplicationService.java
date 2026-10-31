package com.veici.asset.service;

import com.veici.asset.dto.AssetApplicationForm;
import com.veici.asset.entity.ApplicationStatus;
import com.veici.asset.entity.AssetApplication;
import com.veici.asset.entity.AssetStatus;
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

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class AssetApplicationService {

    private final AssetRepository assetRepository;
    private final AppUserRepository userRepository;
    private final AssetApplicationRepository applicationRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public Long submit(
            @NotNull @Positive Long assetId,
            @NotNull @Valid AssetApplicationForm form) {

        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var applicant = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("账号不存在"));

        boolean employee = applicant.isEnabled()
                && applicant.getRoles().stream()
                .anyMatch(role -> "EMPLOYEE".equals(role.getCode()));

        if (!employee) {
            throw new AccessDeniedException("没有领用申请权限");
        }

        if (applicant.getDepartmentId() == null) {
            throw new IllegalArgumentException(
                    "当前账号未分配部门，请联系系统管理员");
        }

        // 获取资产行锁后再检查状态。
        var asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        if (asset.getStatus() != AssetStatus.IDLE) {
            throw new IllegalArgumentException(
                    "资产当前不是闲置状态，无法申请");
        }

        boolean duplicate =
                applicationRepository.existsByApplicantIdAndAsset_IdAndStatusIn(
                        applicant.getId(),
                        assetId,
                        List.of(
                                ApplicationStatus.PENDING,
                                ApplicationStatus.APPROVED
                        )
                );

        if (duplicate) {
            throw new IllegalArgumentException(
                    "你已提交该资产的待审批或已批准申请，请勿重复提交");
        }

        var application = AssetApplication.pending(
                asset,
                applicant.getId(),
                applicant.getDepartmentId(),
                form.getPurpose()
        );

        applicationRepository.saveAndFlush(application);

        logRepository.recordApplicationCreated(
                applicant.getId(),
                application.getId(),
                asset.getId(),
                application.getPurpose()
        );

        return application.getId();
    }
}