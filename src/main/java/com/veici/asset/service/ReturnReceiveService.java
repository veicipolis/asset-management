package com.veici.asset.service;

import com.veici.asset.entity.AssetStatus;
import com.veici.asset.entity.ReturnStatus;
import com.veici.asset.repository.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import com.veici.asset.dto.ReturnReceiveView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class ReturnReceiveService {

    private final AppUserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetLoanRepository loanRepository;
    private final ReturnApplicationRepository returnRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public void receive(
            @NotNull @Positive Long returnId,
            @NotNull @PositiveOrZero Long expectedVersion) {

        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("账号不存在"));

        boolean assetAdmin = user.isEnabled()
                && user.getRoles().stream()
                .anyMatch(role -> "ASSET_ADMIN".equals(role.getCode()));

        if (!assetAdmin) {
            throw new AccessDeniedException("只有资产管理员可以接收归还");
        }

        // 先查编号，不提前加载待修改的实体。
        Long loanId = returnRepository.findLoanIdByReturnId(returnId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "归还申请不存在"));

        Long assetId = loanRepository.findAssetIdByLoanId(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用记录不存在"));

        // 固定加锁顺序：资产 → 领用记录 → 归还申请。
        var asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        var loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用记录不存在"));

        var request = returnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "归还申请不存在"));

        if (!Objects.equals(request.getLoan().getId(), loan.getId())
                || !Objects.equals(loan.getAsset().getId(), asset.getId())
                || !Objects.equals(
                request.getApplicantId(), loan.getBorrowerId())) {
            throw new IllegalArgumentException("归还申请与领用记录不匹配");
        }

        if (request.getStatus() != ReturnStatus.PENDING) {
            throw new IllegalArgumentException("该归还申请已处理或已取消");
        }

        if (!Objects.equals(request.getVersion(), expectedVersion)) {
            throw new IllegalArgumentException("申请已发生变化，请刷新后重试");
        }

        if (loan.getReturnedAt() != null || loan.getReceivedBy() != null) {
            throw new IllegalArgumentException("该领用记录已经归还");
        }

        if (asset.getStatus() != AssetStatus.IN_USE) {
            throw new IllegalArgumentException("资产当前不是使用中状态");
        }

        LocalDateTime receivedAt = LocalDateTime.now();

        loan.receiveReturn(user.getId(), receivedAt);
        request.complete(receivedAt);
        asset.markReturned();

        // 三个实体都处于当前事务中，flush 会同步全部修改。
        returnRepository.flush();

        logRepository.recordReturnReceived(
                user.getId(),
                request.getId(),
                loan.getId(),
                asset.getId()
        );
    }

    @Transactional(readOnly = true)
    public Page<ReturnReceiveView> findPending(int page) {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("账号不存在"));

        boolean assetAdmin = user.isEnabled()
                && user.getRoles().stream()
                .anyMatch(role -> "ASSET_ADMIN".equals(role.getCode()));

        if (!assetAdmin) {
            throw new AccessDeniedException("只有资产管理员可以查看待接收申请");
        }

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.ASC, "createdAt", "id")
        );

        return returnRepository.findByStatus(ReturnStatus.PENDING, pageable)
                .map(item -> new ReturnReceiveView(
                        item.getId(),
                        item.getVersion(),
                        item.getLoan().getId(),
                        item.getApplicantId(),
                        item.getLoan().getAsset().getAssetCode(),
                        item.getLoan().getAsset().getName(),
                        item.getCreatedAt()
                ));
    }
}