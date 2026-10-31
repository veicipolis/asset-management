package com.veici.asset.service;

import com.veici.asset.dto.AssetCreateForm;
import com.veici.asset.entity.Asset;
import com.veici.asset.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final OperationLogRepository logRepository;

    @Transactional
    public Long create(@NotNull @Valid AssetCreateForm form) {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("请先登录");
        }

        // 操作者从登录身份获取，不接受表单传入的用户编号。
        var actor = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("账号不存在"));

        boolean isAssetAdmin = actor.getRoles().stream()
                .anyMatch(role -> "ASSET_ADMIN".equals(role.getCode()));

        if (!actor.isEnabled() || !isAssetAdmin) {
            throw new AccessDeniedException("没有资产建档权限");
        }

        String assetCode = form.getAssetCode().strip();

        if (assetRepository.existsByAssetCode(assetCode)) {
            throw new IllegalArgumentException("资产编号已存在");
        }

        var category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() ->
                        new IllegalArgumentException("所选资产分类不存在"));

        var asset = new Asset();
        asset.setAssetCode(assetCode);
        asset.setName(form.getName().strip());
        asset.setCategory(category);
        asset.setPurchaseDate(form.getPurchaseDate());
        asset.setOriginalValue(form.getOriginalValue());
        asset.setLocation(form.getLocation().strip());

        // 默认状态由实体初始化为 IDLE。
        assetRepository.saveAndFlush(asset);

        logRepository.recordAssetCreated(
                actor.getId(),
                asset.getId(),
                asset.getAssetCode(),
                asset.getName()
        );

        return asset.getId();
    }
}