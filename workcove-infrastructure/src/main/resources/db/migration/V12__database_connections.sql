CREATE TABLE `database_connection` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT       NOT NULL,
    `workspace_id`        VARCHAR(64)  NOT NULL,
    `name`                VARCHAR(80)  NOT NULL,
    `database_type`       VARCHAR(16)  NOT NULL,
    `host`                VARCHAR(255) NOT NULL,
    `port`                INT          NOT NULL,
    `database_name`       VARCHAR(128) NOT NULL DEFAULT '',
    `username`            VARCHAR(128) NOT NULL,
    `password_ciphertext` TEXT         NOT NULL,
    `ssl_enabled`         BOOLEAN      NOT NULL DEFAULT FALSE,
    `created_at`          DATETIME(3)  NOT NULL,
    `updated_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_database_connection_workspace` (
        `owner_id`,
        `workspace_id`,
        `updated_at`
    ),
    CONSTRAINT `fk_database_connection_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户数据库连接配置';
