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
import com.veici.asset.dto.ReturnCancelForm;
import com.veici.asset.service.ReturnCancelService;
import com.veici.asset.service.ReturnReceiveService;
import com.veici.asset.dto.UserEnabledForm;
import com.veici.asset.dto.UserRolesForm;
import com.veici.asset.service.UserAdminService;
import java.util.Set;

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

    @Autowired
    ReturnReceiveService returnReceiveService;

    @Autowired
    ReturnCancelService returnCancelService;

    @Autowired
    UserAdminService userAdminService;

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
        var usernames = List.of(
                prefix + "_admin", prefix + "_e1",
                prefix + "_e2", prefix + "_approver");

        for (String username : usernames) {
            jdbc.update("""
                DELETE FROM operation_log
                WHERE actor_id IN (
                    SELECT id FROM app_user WHERE username = ?
                )
                """, username);
        }

        jdbc.update("""
            DELETE FROM return_application
            WHERE loan_id IN (
                SELECT id FROM asset_loan
                WHERE asset_id IN (
                    SELECT id FROM asset WHERE asset_code = ?
                )
            )
            """, assetCode);

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

    @Test
    void receiveAndCancelCannotBothSucceed() throws Exception {
        assertEquals("asset_management_test",
                jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("asset_test@localhost",
                jdbc.queryForObject("SELECT CURRENT_USER()", String.class));

        String prefix = "return_" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);
        String adminName = prefix + "_admin";
        String employeeName = prefix + "_e1";
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
            long employeeId = createUser(
                    employeeName, departmentId, "EMPLOYEE");
            long approverId = createUser(
                    prefix + "_approver", departmentId, "DEPT_APPROVER");

            jdbc.update("""
                INSERT INTO asset
                    (asset_code, name, category_id, purchase_date,
                     original_value, location, status, version)
                VALUES (?, '归还竞争测试资产', ?, '2026-01-01',
                        1000, '测试库房', 'IN_USE', 1)
                """, assetCode, categoryId);

            long assetId = jdbc.queryForObject(
                    "SELECT id FROM asset WHERE asset_code = ?",
                    Long.class, assetCode);

            long applicationId = createApplication(
                    assetId, employeeId, departmentId, approverId);

            jdbc.update("""
                UPDATE asset_application
                SET status = 'ISSUED', version = 2
                WHERE id = ?
                """, applicationId);

            // 固定历史时间，避免数据库与 JVM 时区差异干扰本测试。
            jdbc.update("""
                INSERT INTO asset_loan
                    (application_id, asset_id, borrower_id,
                     issued_by, issued_at)
                VALUES (?, ?, ?, ?, '2020-01-01 09:00:00')
                """, applicationId, assetId, employeeId, adminId);

            long loanId = jdbc.queryForObject(
                    "SELECT id FROM asset_loan WHERE application_id = ?",
                    Long.class, applicationId);

            jdbc.update("""
                INSERT INTO return_application
                    (loan_id, applicant_id, status, created_at, version)
                VALUES (?, ?, 'PENDING', '2020-01-02 09:00:00', 0)
                """, loanId, employeeId);

            long returnId = jdbc.queryForObject(
                    "SELECT id FROM return_application WHERE loan_id = ?",
                    Long.class, loanId);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Boolean> receive = pool.submit(() ->
                    runReturnOperation(
                            adminName, "ASSET_ADMIN", ready, start,
                            () -> returnReceiveService.receive(returnId, 0L),
                            "该归还申请已处理或已取消"));

            Future<Boolean> cancel = pool.submit(() ->
                    runReturnOperation(
                            employeeName, "EMPLOYEE", ready, start,
                            () -> {
                                var form = new ReturnCancelForm();
                                form.setVersion(0L);
                                returnCancelService.cancel(returnId, form);
                            },
                            "归还申请已发生变化，请刷新页面后重试"));

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "工作线程未及时准备就绪");
            start.countDown();

            boolean received = receive.get(30, TimeUnit.SECONDS);
            boolean cancelled = cancel.get(30, TimeUnit.SECONDS);

            assertNotEquals(received, cancelled,
                    "接收与取消必须恰好一个成功");

            assertEquals(received ? "COMPLETED" : "CANCELLED",
                    jdbc.queryForObject(
                            "SELECT status FROM return_application WHERE id = ?",
                            String.class, returnId));

            assertEquals(received ? "IDLE" : "IN_USE",
                    jdbc.queryForObject(
                            "SELECT status FROM asset WHERE id = ?",
                            String.class, assetId));

            assertEquals(received ? 0L : 1L, count("""
                SELECT COUNT(*) FROM asset_loan
                WHERE id = ? AND returned_at IS NULL
                """, loanId));

            if (received) {
                assertEquals(adminId, jdbc.queryForObject(
                        "SELECT received_by FROM asset_loan WHERE id = ?",
                        Long.class, loanId).longValue());

                assertEquals(1L, count("""
                    SELECT COUNT(*) FROM return_application
                    WHERE id = ? AND completed_at IS NOT NULL
                        AND cancelled_at IS NULL
                    """, returnId));
            } else {
                assertNull(jdbc.queryForObject(
                        "SELECT received_by FROM asset_loan WHERE id = ?",
                        Long.class, loanId));

                assertEquals(1L, count("""
                    SELECT COUNT(*) FROM return_application
                    WHERE id = ? AND cancelled_at IS NOT NULL
                        AND completed_at IS NULL
                    """, returnId));
            }

            assertEquals(1L, count("""
                SELECT COUNT(*) FROM operation_log
                WHERE actor_id IN (?, ?)
                """, adminId, employeeId));

            long winningActor = received ? adminId : employeeId;
            assertEquals(1L, count(
                    "SELECT COUNT(*) FROM operation_log WHERE actor_id = ?",
                    winningActor));
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "工作线程未结束，保留测试数据：" + prefix);
            }
            cleanup(prefix, assetCode);
        }
    }

    private boolean runReturnOperation(
            String username,
            String role,
            CountDownLatch ready,
            CountDownLatch start,
            Runnable operation,
            String expectedConflictMessage) throws Exception {

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);

        try {
            ready.countDown();
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待开始信号超时");
            }
            operation.run();
            return true;
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedConflictMessage, exception.getMessage());
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void concurrentDisablingMustKeepOneSystemAdmin() throws Exception {
        assertEquals("asset_management_test",
                jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("asset_test@localhost",
                jdbc.queryForObject("SELECT CURRENT_USER()", String.class));

        String adminCountSql = """
            SELECT COUNT(*) FROM app_user u
            WHERE u.enabled = TRUE
              AND EXISTS (
                  SELECT 1 FROM user_role ur
                  JOIN app_role r ON r.id = ur.role_id
                  WHERE ur.user_id = u.id AND r.code = 'SYSTEM_ADMIN'
              )
            """;

        // 此场景必须只有本次创建的两个管理员，不修改其他已有账号。
        assertEquals(0L, count(adminCountSql),
                "测试库已有启用的系统管理员，请先检查测试数据，不要直接删除账号");

        String prefix = "admin_" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);
        String firstName = prefix + "_admin";
        String secondName = prefix + "_e1";

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Long departmentId = jdbc.queryForObject(
                    "SELECT MIN(id) FROM department", Long.class);
            assertNotNull(departmentId);

            long firstId = createUser(
                    firstName, departmentId, "SYSTEM_ADMIN");
            long secondId = createUser(
                    secondName, departmentId, "SYSTEM_ADMIN");

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<Boolean> first = pool.submit(() ->
                    runReturnOperation(
                            firstName, "SYSTEM_ADMIN", ready, start,
                            () -> {
                                var form = new UserEnabledForm();
                                form.setVersion(0L);
                                form.setEnabled(false);
                                userAdminService.changeEnabled(firstId, form);
                            },
                            "不能停用最后一个启用的系统管理员"));

            Future<Boolean> second = pool.submit(() ->
                    runReturnOperation(
                            secondName, "SYSTEM_ADMIN", ready, start,
                            () -> {
                                var form = new UserEnabledForm();
                                form.setVersion(0L);
                                form.setEnabled(false);
                                userAdminService.changeEnabled(secondId, form);
                            },
                            "不能停用最后一个启用的系统管理员"));

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "工作线程未及时准备就绪");
            start.countDown();

            boolean firstDisabled = first.get(30, TimeUnit.SECONDS);
            boolean secondDisabled = second.get(30, TimeUnit.SECONDS);

            assertNotEquals(firstDisabled, secondDisabled,
                    "必须恰好一个停用成功");
            assertEquals(1L, count(adminCountSql),
                    "必须保留一个启用的系统管理员");

            long remainingId = firstDisabled ? secondId : firstId;
            String remainingName = firstDisabled ? secondName : firstName;
            long disabledId = firstDisabled ? firstId : secondId;

            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    "SELECT enabled FROM app_user WHERE id = ?",
                    Boolean.class, remainingId));
            assertEquals(Boolean.FALSE, jdbc.queryForObject(
                    "SELECT enabled FROM app_user WHERE id = ?",
                    Boolean.class, disabledId));

            // 再验证：最后一个管理员不能把自己改成普通员工。
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            remainingName, null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_SYSTEM_ADMIN"))));
            SecurityContextHolder.setContext(context);

            Long employeeRoleId = jdbc.queryForObject(
                    "SELECT id FROM app_role WHERE code = 'EMPLOYEE'",
                    Long.class);

            Long beforeVersion = jdbc.queryForObject(
                    "SELECT version FROM app_user WHERE id = ?",
                    Long.class, remainingId);

            var rolesForm = new UserRolesForm();
            rolesForm.setVersion(beforeVersion);
            rolesForm.setRoleIds(Set.of(employeeRoleId));

            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> userAdminService.updateRoles(remainingId, rolesForm));

            assertEquals(
                    "不能移除最后一个启用的系统管理员的管理员角色",
                    exception.getMessage());

            assertEquals(1L, count(adminCountSql));
            assertEquals(beforeVersion, jdbc.queryForObject(
                    "SELECT version FROM app_user WHERE id = ?",
                    Long.class, remainingId));

            assertEquals(1L, count("""
                SELECT COUNT(*) FROM operation_log
                WHERE actor_id IN (?, ?)
                """, firstId, secondId),
                    "只有成功停用应产生一条日志，两个被拒绝的操作不能新增日志");
        } finally {
            SecurityContextHolder.clearContext();
            pool.shutdownNow();

            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "工作线程未结束，保留测试数据：" + prefix);
            }

            cleanup(prefix, prefix + "_asset");
        }
    }
}