CREATE TABLE `test_environment` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT        NOT NULL,
    `workspace_id`        VARCHAR(64)   NOT NULL,
    `name`                VARCHAR(80)   NOT NULL,
    `url`                 VARCHAR(2048) NOT NULL,
    `username`            VARCHAR(256)  NOT NULL,
    `password_ciphertext` TEXT          NOT NULL,
    `created_at`          DATETIME(3)   NOT NULL,
    `updated_at`          DATETIME(3)   NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_test_environment_scope`
        (`owner_id`, `workspace_id`, `updated_at`),
    CONSTRAINT `fk_test_environment_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Codex 测试环境';
