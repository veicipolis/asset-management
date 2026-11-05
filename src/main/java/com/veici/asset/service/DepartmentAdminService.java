package com.veici.asset.service;

import com.veici.asset.dto.DepartmentForm;
import com.veici.asset.entity.AppUser;
import com.veici.asset.entity.Department;
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
public class DepartmentAdminService {

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository userRepository;
    private final AppRoleRepository roleRepository;
    private final OperationLogRepository logRepository;

    @Transactional(readOnly = true)
    public Page<Department> findDepartments(int page) {
        requireSystemAdmin();

        return departmentRepository.findAll(PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.ASC, "id")
        ));
    }

    @Transactional(readOnly = true)
    public DepartmentForm getEditForm(@NotNull @Positive Long id) {
        requireSystemAdmin();

        var department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "部门不存在"));

        var form = new DepartmentForm();
        form.setName(department.getName());
        form.setVersion(department.getVersion());
        return form;
    }

    @Transactional
    public Long create(@NotNull @Valid DepartmentForm form) {
        lockAdminRole();
        var operator = requireSystemAdmin();

        String name = form.getName().strip();

        if (departmentRepository.existsByName(name)) {
            throw new IllegalArgumentException("部门名称已存在");
        }

        var department = Department.create(name);
        departmentRepository.saveAndFlush(department);

        logRepository.recordDepartmentChanged(
                operator.getId(),
                department.getId(),
                true,
                null,
                department.getName(),
                null,
                department.getVersion()
        );

        return department.getId();
    }

    @Transactional
    public void update(
            @NotNull @Positive Long id,
            @NotNull @Valid DepartmentForm form) {

        lockAdminRole();
        var operator = requireSystemAdmin();

        var department = departmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "部门不存在"));

        if (form.getVersion() == null) {
            throw new IllegalArgumentException("缺少版本号，请重新打开编辑页面");
        }

        if (!Objects.equals(department.getVersion(), form.getVersion())) {
            throw new IllegalArgumentException(
                    "部门信息已发生变化，请重新打开编辑页面");
        }

        String name = form.getName().strip();

        if (Objects.equals(department.getName(), name)) {
            throw new IllegalArgumentException("部门名称未发生变化");
        }

        if (departmentRepository.existsByNameAndIdNot(name, id)) {
            throw new IllegalArgumentException("部门名称已存在");
        }

        String beforeName = department.getName();
        Long beforeVersion = department.getVersion();

        department.rename(name);
        departmentRepository.flush();

        logRepository.recordDepartmentChanged(
                operator.getId(),
                department.getId(),
                false,
                beforeName,
                department.getName(),
                beforeVersion,
                department.getVersion()
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
            throw new AccessDeniedException("只有系统管理员可以管理部门");
        }

        return user;
    }
}