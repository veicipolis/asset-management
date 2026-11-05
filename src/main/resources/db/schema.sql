SET NAMES utf8mb4;
USE asset_management;

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