USE asset_management_test;


-- 部门
CREATE TABLE department (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            name VARCHAR(100) NOT NULL,
                            version BIGINT NOT NULL DEFAULT 0,
                            CONSTRAINT uk_department_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户：密码字段只保存安全哈希
CREATE TABLE app_user (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          username VARCHAR(50) NOT NULL,
                          password_hash VARCHAR(255) NOT NULL,
                          display_name VARCHAR(100) NOT NULL,
                          department_id BIGINT NULL,
                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                          version BIGINT NOT NULL DEFAULT 0,
                          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                          CONSTRAINT uk_user_username UNIQUE (username),
                          CONSTRAINT fk_user_department FOREIGN KEY (department_id)
                              REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 固定角色
CREATE TABLE app_role (
                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                          code VARCHAR(40) NOT NULL,
                          name VARCHAR(50) NOT NULL,
                          CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户与角色的多对多关联
CREATE TABLE user_role (
                           user_id BIGINT NOT NULL,
                           role_id BIGINT NOT NULL,
                           PRIMARY KEY (user_id, role_id),
                           CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
                               REFERENCES app_user(id),
                           CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
                               REFERENCES app_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 资产类别
CREATE TABLE asset_category (
                                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                name VARCHAR(100) NOT NULL,
                                version BIGINT NOT NULL DEFAULT 0,
                                CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 资产档案
CREATE TABLE asset (
                       id BIGINT PRIMARY KEY AUTO_INCREMENT,
                       asset_code VARCHAR(50) NOT NULL,
                       name VARCHAR(100) NOT NULL,
                       category_id BIGINT NOT NULL,
                       purchase_date DATE NOT NULL,
                       original_value DECIMAL(15, 2) NOT NULL,
                       location VARCHAR(100) NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
                       version BIGINT NOT NULL DEFAULT 0,
                       created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                       CONSTRAINT uk_asset_code UNIQUE (asset_code),
                       CONSTRAINT fk_asset_category FOREIGN KEY (category_id)
                           REFERENCES asset_category(id),
                       CONSTRAINT ck_asset_value CHECK (original_value >= 0),
                       CONSTRAINT ck_asset_status CHECK (
                           status IN ('IDLE', 'IN_USE', 'MAINTENANCE', 'SCRAPPED')
                           ),

                       INDEX idx_asset_status_category (status, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 领用申请
CREATE TABLE asset_application (
                                   id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                   asset_id BIGINT NOT NULL,
                                   applicant_id BIGINT NOT NULL,
                                   department_id BIGINT NOT NULL,
                                   purpose VARCHAR(200) NOT NULL,
                                   status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                   approver_id BIGINT NULL,
                                   approval_comment VARCHAR(200) NULL,
                                   approved_at DATETIME(6) NULL,
                                   cancelled_at DATETIME(6) NULL,
                                   version BIGINT NOT NULL DEFAULT 0,
                                   created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- 未结束申请标记；结束后自动变为NULL，允许保留多条历史记录
                                   active_marker TINYINT GENERATED ALWAYS AS (
                                       CASE
                                           WHEN status IN ('PENDING', 'APPROVED') THEN 1
                                           ELSE NULL
                                           END
                                       ) STORED,

                                   CONSTRAINT fk_application_asset FOREIGN KEY (asset_id)
                                       REFERENCES asset(id),
                                   CONSTRAINT fk_application_user FOREIGN KEY (applicant_id)
                                       REFERENCES app_user(id),
                                   CONSTRAINT fk_application_department FOREIGN KEY (department_id)
                                       REFERENCES department(id),
                                   CONSTRAINT fk_application_approver FOREIGN KEY (approver_id)
                                       REFERENCES app_user(id),

                                   CONSTRAINT ck_application_status CHECK (
                                       status IN (
                                                  'PENDING', 'APPROVED', 'REJECTED', 'ISSUED', 'CANCELLED'
                                           )
                                       ),
                                   CONSTRAINT ck_application_purpose CHECK (
                                       CHAR_LENGTH(TRIM(purpose)) > 0
                                       ),
                                   CONSTRAINT ck_application_no_self_approval CHECK (
                                       approver_id IS NULL OR approver_id <> applicant_id
                                       ),

                                   CONSTRAINT uk_application_active UNIQUE (
                                                                            applicant_id, asset_id, active_marker
                                       ),

                                   INDEX idx_application_department_status (department_id, status),
                                   INDEX idx_application_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 领用记录：returned_at为空表示尚未归还
CREATE TABLE asset_loan (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            application_id BIGINT NOT NULL,
                            asset_id BIGINT NOT NULL,
                            borrower_id BIGINT NOT NULL,
                            issued_by BIGINT NOT NULL,
                            issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                            returned_at DATETIME(6) NULL,
                            received_by BIGINT NULL,
                            version BIGINT NOT NULL DEFAULT 0,

                            active_marker TINYINT GENERATED ALWAYS AS (
                                CASE WHEN returned_at IS NULL THEN 1 ELSE NULL END
                                ) STORED,

                            CONSTRAINT uk_loan_application UNIQUE (application_id),
                            CONSTRAINT uk_loan_active_asset UNIQUE (asset_id, active_marker),

                            CONSTRAINT fk_loan_application FOREIGN KEY (application_id)
                                REFERENCES asset_application(id),
                            CONSTRAINT fk_loan_asset FOREIGN KEY (asset_id)
                                REFERENCES asset(id),
                            CONSTRAINT fk_loan_borrower FOREIGN KEY (borrower_id)
                                REFERENCES app_user(id),
                            CONSTRAINT fk_loan_issuer FOREIGN KEY (issued_by)
                                REFERENCES app_user(id),
                            CONSTRAINT fk_loan_receiver FOREIGN KEY (received_by)
                                REFERENCES app_user(id),

                            CONSTRAINT ck_loan_return CHECK (
                                (returned_at IS NULL AND received_by IS NULL)
                                    OR
                                (returned_at IS NOT NULL AND received_by IS NOT NULL
                                    AND returned_at >= issued_at)
                                ),

                            INDEX idx_loan_borrower (borrower_id, returned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 归还申请
CREATE TABLE return_application (
                                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                    loan_id BIGINT NOT NULL,
                                    applicant_id BIGINT NOT NULL,
                                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                    completed_at DATETIME(6) NULL,
                                    cancelled_at DATETIME(6) NULL,
                                    version BIGINT NOT NULL DEFAULT 0,

                                    pending_marker TINYINT GENERATED ALWAYS AS (
                                        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
                                        ) STORED,

                                    CONSTRAINT uk_return_pending UNIQUE (loan_id, pending_marker),
                                    CONSTRAINT fk_return_loan FOREIGN KEY (loan_id)
                                        REFERENCES asset_loan(id),
                                    CONSTRAINT fk_return_applicant FOREIGN KEY (applicant_id)
                                        REFERENCES app_user(id),
                                    CONSTRAINT ck_return_status CHECK (
                                        status IN ('PENDING', 'COMPLETED', 'CANCELLED')
                                        ),

                                    INDEX idx_return_applicant (applicant_id, created_at),
                                    INDEX idx_return_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 成功操作的审计日志：区分业务日志与系统管理日志
CREATE TABLE operation_log (
                               id BIGINT PRIMARY KEY AUTO_INCREMENT,
                               actor_id BIGINT NOT NULL,
                               log_category VARCHAR(20) NOT NULL,
                               action VARCHAR(50) NOT NULL,
                               target_type VARCHAR(50) NOT NULL,
                               target_id BIGINT NOT NULL,
                               result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
                               details JSON NULL,
                               created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                               CONSTRAINT fk_log_actor FOREIGN KEY (actor_id)
                                   REFERENCES app_user(id),
                               CONSTRAINT ck_log_category CHECK (
                                   log_category IN ('BUSINESS', 'ADMIN')
                                   ),
                               CONSTRAINT ck_log_result CHECK (result = 'SUCCESS'),

                               INDEX idx_log_category_time (log_category, created_at),
                               INDEX idx_log_target (target_type, target_id),
                               INDEX idx_log_action_time (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


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