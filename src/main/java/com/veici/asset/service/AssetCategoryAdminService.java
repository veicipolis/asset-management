package com.veici.asset.service;

import com.veici.asset.dto.AssetCategoryForm;
import com.veici.asset.entity.AppUser;
import com.veici.asset.entity.AssetCategory;
import com.veici.asset.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class AssetCategoryAdminService {

    private final AssetCategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final OperationLogRepository logRepository;

    @Transactional(readOnly = true)
    public Page<AssetCategory> findCategories(int page) {
        requireSystemAdmin();

        return categoryRepository.findAll(PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.ASC, "id")
        ));
    }

    @Transactional(readOnly = true)
    public AssetCategoryForm getEditForm(@NotNull @Positive Long id) {
        requireSystemAdmin();

        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产类别不存在"));

        var form = new AssetCategoryForm();
        form.setName(category.getName());
        form.setVersion(category.getVersion());
        return form;
    }

    @Transactional
    public Long create(@NotNull @Valid AssetCategoryForm form) {
        lockAdminRole();
        var operator = requireSystemAdmin();

        String name = form.getName().strip();

        if (categoryRepository.existsByName(name)) {
            throw new IllegalArgumentException("类别名称已存在");
        }

        var category = AssetCategory.create(name);
        categoryRepository.saveAndFlush(category);

        logRepository.recordCategoryChanged(
                operator.getId(),
                category.getId(),
                true,
                null,
                category.getName(),
                null,
                category.getVersion()
        );

        return category.getId();
    }

    @Transactional
    public void update(
            @NotNull @Positive Long id,
            @NotNull @Valid AssetCategoryForm form) {

        lockAdminRole();
        var operator = requireSystemAdmin();

        var category = categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产类别不存在"));

        if (form.getVersion() == null) {
            throw new IllegalArgumentException("缺少版本号，请重新打开编辑页面");
        }

        if (!Objects.equals(category.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException(
                    "类别信息已发生变化，请重新打开编辑页面");
        }

        String name = form.getName().strip();

        if (Objects.equals(category.getName(), name)) {
            throw new IllegalArgumentException("类别名称未发生变化");
        }

        if (categoryRepository.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("类别名称已存在");
        }

        String beforeName = category.getName();
        Long beforeVersion = category.getVersion();

        category.rename(name);
        categoryRepository.flush();

        logRepository.recordCategoryChanged(
                operator.getId(),
                category.getId(),
                false,
                beforeName,
                category.getName(),
                beforeVersion,
                category.getVersion()
        );
    }

    private void lockAdminRole() {
        roleRepository.findByCodeForUpdate("SYSTEM_ADMIN")
                .orElseThrow(() ->
                        new IllegalStateException("缺少系统管理员角色配置"));
    }

    private AppUser requireSystemAdmin() {
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

        boolean systemAdmin = user.isEnabled()
                && user.getRoles().stream()
                .anyMatch(role -> "SYSTEM_ADMIN".equals(role.getCode()));

        if (!systemAdmin) {
            throw new AccessDeniedException("只有系统管理员可以管理资产类别");
        }

        return user;
    }
}