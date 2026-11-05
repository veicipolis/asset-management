package com.veici.asset.service;

import com.veici.asset.dto.OperationLogView;
import com.veici.asset.repository.AppUserRepository;
import com.veici.asset.repository.OperationLogReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminOperationLogService {

    private final AppUserRepository userRepository;
    private final OperationLogReadRepository logRepository;

    @Transactional(readOnly = true)
    public Page<OperationLogView> search(
            LocalDate startDate,
            LocalDate endDate,
            String action,
            String targetType,
            Long targetId,
            int page) {

        requireSystemAdmin();

        validateDate(startDate);
        validateDate(endDate);

        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }

        if (targetId != null && targetId <= 0) {
            throw new IllegalArgumentException("对象编号必须大于零");
        }

        String normalizedAction = normalize(action);
        String normalizedTargetType = normalize(targetType);

        if (normalizedAction != null && normalizedAction.length() > 50) {
            throw new IllegalArgumentException("操作类型不能超过50个字符");
        }

        if (normalizedTargetType != null
                && normalizedTargetType.length() > 50) {
            throw new IllegalArgumentException("对象类型不能超过50个字符");
        }

        LocalDateTime startTime = startDate == null
                ? null : startDate.atStartOfDay();

        // 结束日期包含当天，以“次日零点之前”查询。
        LocalDateTime endTime = endDate == null
                ? null : endDate.plusDays(1).atStartOfDay();

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        return logRepository.searchAdmin(
                startTime,
                endTime,
                normalizedAction,
                normalizedTargetType,
                targetId,
                pageable
        ).map(log -> new OperationLogView(
                log.getId(),
                log.getActor().getUsername(),
                log.getCreatedAt(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getResult(),
                log.getDetails()
        ));
    }

    private void requireSystemAdmin() {
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
            throw new AccessDeniedException("只有系统管理员可以查看管理日志");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void validateDate(LocalDate date) {
        if (date != null
                && (date.getYear() < 1000 || date.getYear() > 9998)) {
            throw new IllegalArgumentException("查询年份应在1000至9998之间");
        }
    }
}