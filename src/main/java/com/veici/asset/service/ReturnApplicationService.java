package com.veici.asset.service;

import com.veici.asset.entity.AssetStatus;
import com.veici.asset.entity.ReturnApplication;
import com.veici.asset.entity.ReturnStatus;
import com.veici.asset.repository.*;
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
import com.veici.asset.dto.MyLoanView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class ReturnApplicationService {

    private final AppUserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetLoanRepository loanRepository;
    private final ReturnApplicationRepository returnRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public Long submit(@NotNull @Positive Long loanId) {
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
            throw new AccessDeniedException("没有提交归还申请的权限");
        }

        Long assetId = loanRepository.findAssetIdByLoanId(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用记录不存在"));

        // 固定加锁顺序：资产 → 领用记录。
        var asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        var loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "领用记录不存在"));

        if (!Objects.equals(loan.getBorrowerId(), user.getId())) {
            throw new AccessDeniedException("只能归还自己领用的资产");
        }

        if (loan.getReturnedAt() != null) {
            throw new IllegalArgumentException("该领用记录已经归还");
        }

        if (asset.getStatus() != AssetStatus.IN_USE) {
            throw new IllegalArgumentException(
                    "资产当前不是使用中状态，请联系资产管理员核查");
        }

        if (returnRepository.existsByLoan_IdAndStatus(
                loanId, ReturnStatus.PENDING)) {
            throw new IllegalArgumentException(
                    "该领用记录已有待接收的归还申请，请勿重复提交");
        }

        var returnApplication = ReturnApplication.pending(loan);
        returnRepository.saveAndFlush(returnApplication);

        logRepository.recordReturnRequested(
                user.getId(),
                returnApplication.getId(),
                loan.getId(),
                asset.getId()
        );

        return returnApplication.getId();
    }

    @Transactional(readOnly = true)
    public Page<MyLoanView> findMyLoans(int page) {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("账号不存在"));

        boolean employee = user.isEnabled()
                && user.getRoles().stream()
                .anyMatch(role -> "EMPLOYEE".equals(role.getCode()));

        if (!employee) {
            throw new AccessDeniedException("没有查看领用记录的权限");
        }

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.DESC, "issuedAt", "id")
        );

        return loanRepository.findByBorrowerId(user.getId(), pageable)
                .map(loan -> {
                    var pending = returnRepository.findByLoan_IdAndStatus(
                            loan.getId(), ReturnStatus.PENDING);

                    return new MyLoanView(
                            loan.getId(),
                            loan.getAsset().getAssetCode(),
                            loan.getAsset().getName(),
                            loan.getIssuedAt(),
                            loan.getReturnedAt(),
                            pending.isPresent(),
                            pending.map(item -> item.getId()).orElse(null),
                            pending.map(item -> item.getVersion()).orElse(null)
                    );
                });
    }
}