package com.veici.asset.service;

import com.veici.asset.dto.UserAdminView;
import com.veici.asset.entity.AppUser;
import com.veici.asset.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.veici.asset.dto.UserEnabledForm;
import com.veici.asset.repository.AppRoleRepository;
import com.veici.asset.repository.OperationLogRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class UserAdminService {

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final OperationLogRepository logRepository;

    @Transactional(readOnly = true)
    public Page<UserAdminView> findUsers(int page) {
        requireSystemAdmin();

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.ASC, "id")
        );

        return userRepository.findAll(pageable)
                .map(user -> new UserAdminView(
                        user.getId(),
                        user.getVersion(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getDepartmentId(),
                        user.isEnabled(),
                        user.getRoles().stream()
                                .map(role -> role.getCode())
                                .sorted()
                                .collect(Collectors.joining(", "))
                ));
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
            throw new AccessDeniedException("只有系统管理员可以管理用户");
        }

        return user;
    }

    @Transactional
    public void changeEnabled(
            @NotNull @Positive Long userId,
            @NotNull @Valid UserEnabledForm form) {

        // 所有账号启停及后续角色修改，都先取得这把共同的锁。
        roleRepository.findByCodeForUpdate("SYSTEM_ADMIN")
                .orElseThrow(() ->
                        new IllegalStateException("缺少系统管理员角色配置"));

        // 等待锁结束后，再读取操作者的最新权限。
        var operator = requireSystemAdmin();

        var target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "用户不存在"));

        if (!Objects.equals(target.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException("用户信息已发生变化，请刷新后重试");
        }

        boolean requestedEnabled = form.getEnabled();

        if (target.isEnabled() == requestedEnabled) {
            throw new IllegalArgumentException(
                    requestedEnabled ? "该用户已经启用" : "该用户已经停用");
        }

        boolean targetIsSystemAdmin = target.getRoles().stream()
                .anyMatch(role -> "SYSTEM_ADMIN".equals(role.getCode()));

        if (!requestedEnabled
                && targetIsSystemAdmin
                && userRepository.countEnabledSystemAdmins() <= 1) {
            throw new IllegalArgumentException("不能停用最后一个启用的系统管理员");
        }

        boolean beforeEnabled = target.isEnabled();
        Long beforeVersion = target.getVersion();

        target.changeEnabled(requestedEnabled);
        userRepository.flush();

        logRepository.recordUserEnabledChanged(
                operator.getId(),
                target.getId(),
                beforeEnabled,
                target.isEnabled(),
                beforeVersion,
                target.getVersion()
        );
    }
}