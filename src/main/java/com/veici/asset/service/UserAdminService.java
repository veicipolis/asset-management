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
import com.veici.asset.dto.UserCreateForm;
import com.veici.asset.repository.DepartmentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.veici.asset.entity.Department;
import java.util.List;
import com.veici.asset.dto.UserEditForm;

import java.util.Objects;

import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class UserAdminService {

    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final OperationLogRepository logRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional
    public Long create(@NotNull @Valid UserCreateForm form) {
        // 与账号启停、角色修改保持一致的加锁顺序。
        roleRepository.findByCodeForUpdate("SYSTEM_ADMIN")
                .orElseThrow(() ->
                        new IllegalStateException("缺少系统管理员角色配置"));

        var operator = requireSystemAdmin();

        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (form.getDepartmentId() != null
                && !departmentRepository.existsById(form.getDepartmentId())) {
            throw new IllegalArgumentException("所选部门不存在，请重新选择");
        }

        // 密码保持原样参与哈希，不执行 trim 或 strip。
        String passwordHash = passwordEncoder.encode(form.getPassword());

        var user = AppUser.create(
                form.getUsername(),
                form.getDisplayName(),
                form.getDepartmentId(),
                passwordHash
        );

        userRepository.saveAndFlush(user);

        logRepository.recordUserCreated(
                operator.getId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDepartmentId()
        );

        return user.getId();
    }

    @Transactional(readOnly = true)
    public List<Department> findDepartments() {
        requireSystemAdmin();
        return departmentRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public UserEditForm getEditForm(@NotNull @Positive Long userId) {
        requireSystemAdmin();

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "用户不存在"));

        var form = new UserEditForm();
        form.setVersion(user.getVersion());
        form.setDisplayName(user.getDisplayName());
        form.setDepartmentId(user.getDepartmentId());
        return form;
    }

    @Transactional
    public void updateProfile(
            @NotNull @Positive Long userId,
            @NotNull @Valid UserEditForm form) {

        roleRepository.findByCodeForUpdate("SYSTEM_ADMIN")
                .orElseThrow(() ->
                        new IllegalStateException("缺少系统管理员角色配置"));

        var operator = requireSystemAdmin();

        var target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "用户不存在"));

        if (!Objects.equals(target.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException(
                    "用户信息已发生变化，请重新打开编辑页面");
        }

        if (form.getDepartmentId() != null
                && !departmentRepository.existsById(form.getDepartmentId())) {
            throw new IllegalArgumentException("所选部门不存在，请重新选择");
        }

        String newName = form.getDisplayName().strip();

        if (Objects.equals(target.getDisplayName(), newName)
                && Objects.equals(
                target.getDepartmentId(), form.getDepartmentId())) {
            throw new IllegalArgumentException("姓名和部门均未发生变化");
        }

        String beforeName = target.getDisplayName();
        Long beforeDepartmentId = target.getDepartmentId();
        Long beforeVersion = target.getVersion();

        target.updateProfile(newName, form.getDepartmentId());
        userRepository.flush();

        logRepository.recordUserProfileUpdated(
                operator.getId(),
                target.getId(),
                beforeName,
                target.getDisplayName(),
                beforeDepartmentId,
                target.getDepartmentId(),
                beforeVersion,
                target.getVersion()
        );
    }
}