CREATE TABLE `mail_account` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT       NOT NULL,
    `workspace_id`        VARCHAR(64)  NOT NULL,
    `display_name`        VARCHAR(120) NOT NULL,
    `email_address`       VARCHAR(320) NOT NULL,
    `username`            VARCHAR(320) NOT NULL,
    `password_ciphertext` TEXT         NOT NULL,
    `imap_host`           VARCHAR(255) NOT NULL,
    `imap_port`           SMALLINT UNSIGNED NOT NULL,
    `imap_ssl`            BOOLEAN      NOT NULL DEFAULT TRUE,
    `smtp_host`           VARCHAR(255) NOT NULL,
    `smtp_port`           SMALLINT UNSIGNED NOT NULL,
    `smtp_ssl`            BOOLEAN      NOT NULL DEFAULT TRUE,
    `created_at`          DATETIME(3)  NOT NULL,
    `updated_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mail_account_address`
        (`owner_id`, `workspace_id`, `email_address`),
    KEY `idx_mail_account_scope`
        (`owner_id`, `workspace_id`, `updated_at`),
    CONSTRAINT `fk_mail_account_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '工作空间邮件账号';
