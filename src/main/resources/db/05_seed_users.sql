SET NAMES utf8mb4;
USE asset_management;

-- 执行前，必须在同一MySQL连接中设置 @demo_password_hash。
-- 所有账号仅用于本地演示，暂时共用同一个演示密码。

START TRANSACTION;

SET @rd_department = (
    SELECT id FROM department WHERE name = '研发部'
);

SET @admin_department = (
    SELECT id FROM department WHERE name = '行政部'
);

INSERT INTO app_user (
    username, password_hash, display_name, department_id, enabled
) VALUES
      ('employee', @demo_password_hash, '研发员工', @rd_department, TRUE),
      ('employee2', @demo_password_hash, '行政员工', @admin_department, TRUE),
      ('approver', @demo_password_hash, '研发审批人', @rd_department, TRUE),
      ('assetadmin', @demo_password_hash, '资产管理员', @admin_department, TRUE),
      ('sysadmin', @demo_password_hash, '系统管理员', @admin_department, TRUE);

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
         JOIN app_role r ON r.code = 'EMPLOYEE'
WHERE u.username IN ('employee', 'employee2', 'approver');

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
         JOIN app_role r ON r.code = 'DEPT_APPROVER'
WHERE u.username = 'approver';

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
         JOIN app_role r ON r.code = 'ASSET_ADMIN'
WHERE u.username = 'assetadmin';

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
         JOIN app_role r ON r.code = 'SYSTEM_ADMIN'
WHERE u.username = 'sysadmin';

-- 先核对结果，再手动COMMIT；本文件不自动提交。
SELECT
    u.username,
    d.name AS department,
    GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ', ') AS roles
FROM app_user u
         LEFT JOIN department d ON d.id = u.department_id
         LEFT JOIN user_role ur ON ur.user_id = u.id
         LEFT JOIN app_role r ON r.id = ur.role_id
WHERE u.username IN (
                     'employee', 'employee2', 'approver', 'assetadmin', 'sysadmin'
    )
GROUP BY u.id, u.username, d.name
ORDER BY u.username;