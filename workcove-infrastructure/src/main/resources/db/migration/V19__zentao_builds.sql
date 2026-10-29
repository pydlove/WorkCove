CREATE TABLE `zentao_build` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`       BIGINT       NOT NULL,
    `workspace_id`   VARCHAR(64)  NOT NULL,
    `remote_id`      VARCHAR(64)  NOT NULL,
    `project_id`     VARCHAR(64)  NOT NULL,
    `project_name`   VARCHAR(255) NOT NULL,
    `product_id`     VARCHAR(64)  NOT NULL DEFAULT '',
    `product_name`   VARCHAR(255) NOT NULL DEFAULT '',
    `execution_id`   VARCHAR(64)  NOT NULL DEFAULT '',
    `name`           VARCHAR(500) NOT NULL,
    `status`         VARCHAR(64)  NOT NULL DEFAULT '',
    `builder`        VARCHAR(128) NOT NULL DEFAULT '',
    `build_date`     VARCHAR(64)  NOT NULL DEFAULT '',
    `description`    TEXT         NULL,
    `scm_revision`   VARCHAR(255) NOT NULL DEFAULT '',
    `last_synced_at` DATETIME(3)  NOT NULL,
    `created_at`     DATETIME(3)  NOT NULL,
    `updated_at`     DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_zentao_build_remote`
        (`owner_id`, `workspace_id`, `project_id`, `remote_id`),
    KEY `idx_zentao_build_scope`
        (`owner_id`, `workspace_id`, `build_date`, `updated_at`),
    CONSTRAINT `fk_zentao_build_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '从禅道同步的构建清单';
