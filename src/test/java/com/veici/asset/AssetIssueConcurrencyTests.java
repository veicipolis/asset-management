package com.veici.asset;

import com.veici.asset.dto.AssetIssueForm;
import com.veici.asset.service.AssetIssueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AssetIssueConcurrencyTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AssetIssueService issueService;

    @Test
    void onlyOneRequestCanIssueTheSameAsset() throws Exception {
        // 写入之前确认连接的是独立测试库。
        assertEquals("asset_management_test",
                jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("asset_test@localhost",
                jdbc.queryForObject("SELECT CURRENT_USER()", String.class));

        String prefix = "race_" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);
        String adminName = prefix + "_admin";
        String assetCode = prefix + "_asset";

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Long departmentId = jdbc.queryForObject(
                    "SELECT MIN(id) FROM department", Long.class);
            Long categoryId = jdbc.queryForObject(
                    "SELECT MIN(id) FROM asset_category", Long.class);
            assertNotNull(departmentId);
            assertNotNull(categoryId);

            long adminId = createUser(
                    adminName, departmentId, "ASSET_ADMIN");
            long employee1 = createUser(
                    prefix + "_e1", departmentId, "EMPLOYEE");
            long employee2 = createUser(
                    prefix + "_e2", departmentId, "EMPLOYEE");
            long approverId = createUser(
                    prefix + "_approver", departmentId, "DEPT_APPROVER");

            jdbc.update("""
                    INSERT INTO asset
                        (asset_code, name, category_id, purchase_date,
                         original_value, location, status, version)
                    VALUES (?, '并发测试资产', ?, '2026-01-01',
                            1000, '测试库房', 'IDLE', 0)
                    """, assetCode, categoryId);

            long assetId = jdbc.queryForObject(
                    "SELECT id FROM asset WHERE asset_code = ?",
                    Long.class, assetCode);

            // 两个不同员工的已批准申请，同时竞争同一资产。
            long application1 = createApplication(
                    assetId, employee1, departmentId, approverId);
            long application2 = createApplication(
                    assetId, employee2, departmentId, approverId);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Boolean> first = pool.submit(() ->
                    tryIssue(adminName, application1, ready, start));
            Future<Boolean> second = pool.submit(() ->
                    tryIssue(adminName, application2, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "两个工作线程未及时准备就绪");
            start.countDown();

            boolean firstSucceeded = first.get(30, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(30, TimeUnit.SECONDS);

            assertNotEquals(firstSucceeded, secondSucceeded,
                    "必须恰好一个成功、另一个被业务校验拦截");

            long winner = firstSucceeded ? application1 : application2;
            long loser = firstSucceeded ? application2 : application1;

            assertEquals("IN_USE", jdbc.queryForObject(
                    "SELECT status FROM asset WHERE id = ?",
                    String.class, assetId));

            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM asset_loan WHERE asset_id = ?",
                    assetId));

            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM asset_loan
                    WHERE asset_id = ? AND returned_at IS NULL
                    """, assetId));

            assertEquals(winner, jdbc.queryForObject(
                    "SELECT application_id FROM asset_loan WHERE asset_id = ?",
                    Long.class, assetId).longValue());

            assertEquals("ISSUED", jdbc.queryForObject(
                    "SELECT status FROM asset_application WHERE id = ?",
                    String.class, winner));

            assertEquals("APPROVED", jdbc.queryForObject(
                    "SELECT status FROM asset_application WHERE id = ?",
                    String.class, loser));

            // 此专用管理员只执行了发放操作，应只有一条成功日志。
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM operation_log WHERE actor_id = ?",
                    adminId));
        } finally {
            pool.shutdownNow();

            // 必须等工作线程结束，再清理它们使用的数据。
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "工作线程未结束，保留测试数据以便排查：" + prefix);
            }

            cleanup(prefix, assetCode);
        }
    }

    private boolean tryIssue(
            String username,
            long applicationId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ASSET_ADMIN"))));
        SecurityContextHolder.setContext(context);

        try {
            ready.countDown();
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待开始信号超时");
            }

            var form = new AssetIssueForm();
            form.setApplicationVersion(1L);
            form.setAssetVersion(0L);

            // 调用 Spring 代理服务，每个线程各自开启独立事务。
            issueService.issue(applicationId, form);
            return true;
        } catch (IllegalArgumentException exception) {
            // 只接受预期的版本冲突，其他异常必须让测试失败。
            assertEquals(
                    "申请或资产已发生变化，请重新打开确认页面",
                    exception.getMessage());
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long createUser(
            String username, long departmentId, String role) {

        // 本测试直接设置认证上下文，不使用密码登录。
        jdbc.update("""
                INSERT INTO app_user
                    (username, password_hash, display_name, department_id)
                VALUES (?, 'TEST_ONLY_NOT_A_LOGIN_HASH', ?, ?)
                """, username, username, departmentId);

        long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE username = ?",
                Long.class, username);

        assertEquals(1, jdbc.update("""
                INSERT INTO user_role (user_id, role_id)
                SELECT ?, id FROM app_role WHERE code = ?
                """, userId, role));

        return userId;
    }

    private long createApplication(
            long assetId, long applicantId,
            long departmentId, long approverId) {

        jdbc.update("""
                INSERT INTO asset_application
                    (asset_id, applicant_id, department_id, purpose,
                     status, approver_id, approval_comment, approved_at, version)
                VALUES (?, ?, ?, '并发发放验证',
                        'APPROVED', ?, '测试批准', CURRENT_TIMESTAMP(6), 1)
                """, assetId, applicantId, departmentId, approverId);

        return jdbc.queryForObject("""
                SELECT id FROM asset_application
                WHERE asset_id = ? AND applicant_id = ?
                """, Long.class, assetId, applicantId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void cleanup(String prefix, String assetCode) {
        // 使用本次随机生成的完整名称，避免影响其他测试的数据。
        var usernames = List.of(
                prefix + "_admin", prefix + "_e1",
                prefix + "_e2", prefix + "_approver");

        jdbc.update("""
                DELETE FROM operation_log
                WHERE actor_id IN (
                    SELECT id FROM app_user WHERE username = ?
                )
                """, prefix + "_admin");

        jdbc.update("""
                DELETE FROM asset_loan
                WHERE asset_id IN (
                    SELECT id FROM asset WHERE asset_code = ?
                )
                """, assetCode);

        jdbc.update("""
                DELETE FROM asset_application
                WHERE asset_id IN (
                    SELECT id FROM asset WHERE asset_code = ?
                )
                """, assetCode);

        jdbc.update("DELETE FROM asset WHERE asset_code = ?", assetCode);

        for (String username : usernames) {
            jdbc.update("""
                    DELETE FROM user_role
                    WHERE user_id IN (
                        SELECT id FROM app_user WHERE username = ?
                    )
                    """, username);
            jdbc.update("DELETE FROM app_user WHERE username = ?", username);
        }
    }
}