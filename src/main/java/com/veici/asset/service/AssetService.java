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
import com.veici.asset.dto.AssetEditForm;
import com.veici.asset.entity.AppUser;
import com.veici.asset.entity.AssetStatus;
import com.veici.asset.exception.AssetEditConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final OperationLogRepository logRepository;
    private final JsonMapper jsonMapper;

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

    @Transactional(readOnly = true)
    public AssetEditForm getEditForm(Long assetId) {
        requireAssetAdmin();
        var asset = findEditableAsset(assetId);

        var form = new AssetEditForm();
        form.setVersion(asset.getVersion());
        form.setName(asset.getName());
        form.setCategoryId(asset.getCategory().getId());
        form.setPurchaseDate(asset.getPurchaseDate());
        form.setOriginalValue(asset.getOriginalValue());
        form.setLocation(asset.getLocation());

        return form;
    }

    @Transactional
    public void update(
            Long assetId,
            @NotNull @Valid AssetEditForm form) {

        var actor = requireAssetAdmin();
        var asset = findEditableAsset(assetId);

        // 检查用户打开表单后，数据是否已发生变化。
        if (!Objects.equals(asset.getVersion(), form.getVersion())) {
            throw new AssetEditConflictException();
        }

        var category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() ->
                        new IllegalArgumentException("所选资产分类不存在"));

        String beforeJson = snapshot(asset);

        asset.setName(form.getName().strip());
        asset.setCategory(category);
        asset.setPurchaseDate(form.getPurchaseDate());
        asset.setOriginalValue(form.getOriginalValue());
        asset.setLocation(form.getLocation().strip());

        // 强制执行更新，让 @Version 在数据库层检查并发冲突。
        assetRepository.flush();

        String afterJson = snapshot(asset);

        logRepository.recordAssetUpdated(
                actor.getId(),
                asset.getId(),
                beforeJson,
                afterJson
        );
    }

    private AppUser requireAssetAdmin() {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("请先登录");
        }

        var actor = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("账号不存在"));

        boolean allowed = actor.isEnabled()
                && actor.getRoles().stream()
                .anyMatch(role -> "ASSET_ADMIN".equals(role.getCode()));

        if (!allowed) {
            throw new AccessDeniedException("没有资产管理权限");
        }

        return actor;
    }

    private Asset findEditableAsset(Long assetId) {
        var asset = assetRepository.findById(assetId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "资产不存在"));

        if (asset.getStatus() == AssetStatus.SCRAPPED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "已报废资产不能编辑");
        }

        return asset;
    }

    private String snapshot(Asset asset) {
        return jsonMapper.writeValueAsString(Map.of(
                "assetCode", asset.getAssetCode(),
                "name", asset.getName(),
                "categoryId", asset.getCategory().getId(),
                "purchaseDate", asset.getPurchaseDate().toString(),
                "originalValue", asset.getOriginalValue(),
                "location", asset.getLocation(),
                "status", asset.getStatus().name(),
                "version", asset.getVersion()
        ));
    }
}