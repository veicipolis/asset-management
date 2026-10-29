USE asset_management;

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