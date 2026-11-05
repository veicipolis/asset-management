package com.veici.asset.service;

import com.veici.asset.dto.ReturnCancelForm;
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

import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class ReturnCancelService {

    private final AppUserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetLoanRepository loanRepository;
    private final ReturnApplicationRepository returnRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public void cancel(
            @NotNull @Positive Long returnId,
            @NotNull @Valid ReturnCancelForm form) {

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
            throw new AccessDeniedException("没有取消归还申请的权限");
        }

        Long loanId = returnRepository.findLoanIdByReturnId(returnId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "归还申请不存在"));

        Long assetId = loanRepository.findAssetIdByLoanId(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用记录不存在"));

        // 与归还接收保持一致的加锁顺序。
        var asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        var loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用记录不存在"));

        var request = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "归还申请不存在"));

        if (!Objects.equals(request.getApplicantId(), user.getId())
                || !Objects.equals(loan.getBorrowerId(), user.getId())) {
            throw new AccessDeniedException("只能取消本人的归还申请");
        }

        if (!Objects.equals(request.getLoan().getId(), loan.getId())
                || !Objects.equals(loan.getAsset().getId(), asset.getId())) {
            throw new IllegalArgumentException("归还申请与领用记录不匹配");
        }

        if (!Objects.equals(request.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException(
                    "归还申请已发生变化，请刷新页面后重试");
        }

        if (loan.getReturnedAt() != null || loan.getReceivedBy() != null) {
            throw new IllegalArgumentException("资产已经接收归还，不能取消");
        }

        if (asset.getStatus() != AssetStatus.IN_USE) {
            throw new IllegalArgumentException(
                    "资产当前不是使用中状态，请联系资产管理员核查");
        }

        Long beforeVersion = request.getVersion();

        // 实体方法只允许取消 PENDING 状态。
        request.cancel();
        returnRepository.flush();

        logRepository.recordReturnCancelled(
                user.getId(),
                request.getId(),
                loan.getId(),
                asset.getId(),
                beforeVersion,
                request.getVersion()
        );
    }
}