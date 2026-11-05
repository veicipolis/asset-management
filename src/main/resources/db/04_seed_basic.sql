SET NAMES utf8mb4;
USE asset_management;

-- 四种固定角色
INSERT INTO app_role (code, name) VALUES
                                      ('EMPLOYEE', '普通员工'),
                                      ('DEPT_APPROVER', '部门审批人'),
                                      ('ASSET_ADMIN', '资产管理员'),
                                      ('SYSTEM_ADMIN', '系统管理员');

-- 演示部门
INSERT INTO department (name) VALUES
                                  ('研发部'),
                                  ('行政部');

-- 资产类别
INSERT INTO asset_category (name) VALUES
                                      ('电脑设备'),
                                      ('办公家具'),
                                      ('其他设备');

-- 演示资产：通过类别名称取得ID，不依赖固定编号
INSERT INTO asset (
    asset_code, name, category_id,
    purchase_date, original_value, location, status
)
SELECT
    'DEMO-PC-001', '演示笔记本电脑', id,
    '2026-01-10', 5000.00, '一号库房', 'IDLE'
FROM asset_category
WHERE name = '电脑设备';

INSERT INTO asset (
    asset_code, name, category_id,
    purchase_date, original_value, location, status
)
SELECT
    'DEMO-DESK-001', '演示办公桌', id,
    '2026-01-10', 800.00, '一号库房', 'IDLE'
FROM asset_category
WHERE name = '办公家具';