SET NAMES utf8mb4;
USE asset_management;

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