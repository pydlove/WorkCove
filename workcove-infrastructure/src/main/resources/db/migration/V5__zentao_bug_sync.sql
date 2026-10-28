CREATE TABLE `zentao_connection` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT       NOT NULL,
    `workspace_id`        VARCHAR(64)  NOT NULL,
    `base_url`            VARCHAR(512) NOT NULL,
    `username`            VARCHAR(128) NOT NULL,
    `password_ciphertext` TEXT         NOT NULL,
    `last_synced_at`      DATETIME(3)  NULL,
    `created_at`          DATETIME(3)  NOT NULL,
    `updated_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_zentao_connection_scope` (`owner_id`, `workspace_id`),
    CONSTRAINT `fk_zentao_connection_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '禅道连接配置';

CREATE TABLE `zentao_bug` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`      BIGINT       NOT NULL,
    `workspace_id`  VARCHAR(64)  NOT NULL,
    `remote_id`     VARCHAR(64)  NOT NULL,
    `product_id`    VARCHAR(64)  NOT NULL,
    `product_name`  VARCHAR(255) NOT NULL,
    `title`         VARCHAR(500) NOT NULL,
    `severity`      VARCHAR(32)  NOT NULL DEFAULT '',
    `priority`      VARCHAR(32)  NOT NULL DEFAULT '',
    `status`        VARCHAR(32)  NOT NULL DEFAULT '',
    `steps`         TEXT         NULL,
    `opened_by`     VARCHAR(128) NOT NULL DEFAULT '',
    `opened_date`   VARCHAR(64)  NOT NULL DEFAULT '',
    `assigned_to`   VARCHAR(128) NOT NULL DEFAULT '',
    `active`        BOOLEAN      NOT NULL DEFAULT TRUE,
    `local_status`  VARCHAR(32)  NOT NULL DEFAULT 'synced',
    `resolution`    VARCHAR(32)  NOT NULL DEFAULT '',
    `solution_text` TEXT         NULL,
    `reason_text`   TEXT         NULL,
    `pushed_at`     DATETIME(3)  NULL,
    `last_synced_at` DATETIME(3) NOT NULL,
    `created_at`    DATETIME(3)  NOT NULL,
    `updated_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_zentao_bug_remote`
        (`owner_id`, `workspace_id`, `product_id`, `remote_id`),
    KEY `idx_zentao_bug_scope`
        (`owner_id`, `workspace_id`, `active`, `updated_at`),
    CONSTRAINT `fk_zentao_bug_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '从禅道同步的 Bug';
