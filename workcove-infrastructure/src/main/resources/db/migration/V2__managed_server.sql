CREATE TABLE `managed_server` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT       NOT NULL,
    `name`                VARCHAR(80)  NOT NULL,
    `host`                VARCHAR(255) NOT NULL,
    `port`                SMALLINT UNSIGNED NOT NULL DEFAULT 22,
    `username`            VARCHAR(128) NOT NULL,
    `password_ciphertext` TEXT         NOT NULL,
    `created_at`          DATETIME(3)  NOT NULL,
    `updated_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_managed_server_owner` (`owner_id`, `updated_at`),
    CONSTRAINT `fk_managed_server_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '研发服务器连接信息';
