package com.veici.asset.service;

import com.veici.asset.dto.AssetIssueForm;
import com.veici.asset.entity.AppUser;
import com.veici.asset.entity.AssetLoan;
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
import com.veici.asset.dto.AssetIssueView;
import com.veici.asset.entity.ApplicationStatus;
import com.veici.asset.entity.AssetApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class AssetIssueService {

    private final AppUserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetApplicationRepository applicationRepository;
    private final AssetLoanRepository loanRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public Long issue(
            @NotNull @Positive Long applicationId,
            @NotNull @Valid AssetIssueForm form) {

        var issuer = requireAssetAdmin();

        Long assetId = applicationRepository
                .findAssetIdByApplicationId(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "申请不存在"));

        // 统一按“资产 → 申请”顺序加锁。
        var asset = assetRepository.findByIdForUpdate(assetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        var application = applicationRepository
                .findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "申请不存在"));

        if (!Objects.equals(
                application.getVersion(), form.getApplicationVersion())
                || !Objects.equals(
                asset.getVersion(), form.getAssetVersion())) {
            throw new IllegalArgumentException(
                    "申请或资产已发生变化，请重新打开确认页面");
        }

        if (loanRepository.existsByApplicationId(applicationId)) {
            throw new IllegalArgumentException("该申请已经发放，请勿重复操作");
        }

        if (loanRepository.existsByAsset_IdAndReturnedAtIsNull(assetId)) {
            throw new IllegalArgumentException("该资产尚未归还，不能重复发放");
        }

        // 创建领用记录时会检查申请为 APPROVED、资产为 IDLE。
        var loan = AssetLoan.issue(application, asset, issuer.getId());

        application.markIssued();
        asset.markInUse();

        // 刷新当前事务中的领用记录、申请和资产变更。
        loanRepository.saveAndFlush(loan);

        logRepository.recordAssetIssued(
                issuer.getId(),
                loan.getId(),
                application.getId(),
                asset.getId(),
                loan.getBorrowerId()
        );

        return loan.getId();
    }

    private AppUser requireAssetAdmin() {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var issuer = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("账号不存在"));

        boolean allowed = issuer.isEnabled()
                && issuer.getRoles().stream()
                .anyMatch(role -> "ASSET_ADMIN".equals(role.getCode()));

        if (!allowed) {
            throw new AccessDeniedException("没有资产发放权限");
        }

        return issuer;
    }

    @Transactional(readOnly = true)
    public Page<AssetIssueView> findApproved(int page) {
        requireAssetAdmin();

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.ASC, "approvedAt", "id")
        );

        return applicationRepository
                .findByStatus(ApplicationStatus.APPROVED, pageable)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public AssetIssueView getForIssue(
            @NotNull @Positive Long applicationId) {

        requireAssetAdmin();

        var application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "申请不存在"));

        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "申请不是已批准状态，请返回待发放列表");
        }

        return toView(application);
    }

    private AssetIssueView toView(AssetApplication application) {
        var asset = application.getAsset();

        return new AssetIssueView(
                application.getId(),
                application.getVersion(),
                asset.getVersion(),
                application.getApplicantId(),
                asset.getAssetCode(),
                asset.getName(),
                application.getPurpose(),
                asset.getStatus()
        );
    }
}