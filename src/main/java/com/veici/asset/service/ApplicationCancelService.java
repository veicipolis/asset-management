package com.veici.asset.service;

import com.veici.asset.dto.ApplicationCancelForm;
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

import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class ApplicationCancelService {

    private final AppUserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetApplicationRepository applicationRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public void cancel(
            @NotNull @Positive Long applicationId,
            @NotNull @Valid ApplicationCancelForm form) {

        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("账号不存在"));

        boolean employee = user.isEnabled()
                && user.getRoles().stream()
                .anyMatch(role -> "EMPLOYEE".equals(role.getCode()));

        if (!employee) {
            throw new AccessDeniedException("没有取消申请的权限");
        }

        Long assetId =
                applicationRepository.findAssetIdByApplicationId(applicationId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "领用申请不存在"));

        // 与审批、发放保持一致：先锁资产，再锁申请。
        assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        var request = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用申请不存在"));

        if (!Objects.equals(request.getApplicantId(), user.getId())) {
            throw new AccessDeniedException("只能取消本人的领用申请");
        }

        if (!Objects.equals(request.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException(
                    "申请已发生变化，请刷新页面后重试");
        }

        String beforeStatus = request.getStatus().name();
        Long beforeVersion = request.getVersion();

        // 实体方法检查是否为 PENDING 或 APPROVED。
        request.cancel();
        applicationRepository.flush();

        logRepository.recordApplicationCancelled(
                user.getId(),
                request.getId(),
                beforeStatus,
                beforeVersion,
                request.getVersion()
        );
    }
}