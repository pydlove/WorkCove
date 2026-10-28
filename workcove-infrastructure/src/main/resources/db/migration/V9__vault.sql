CREATE TABLE `vault_entry` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT       NOT NULL,
    `name`                VARCHAR(100) NOT NULL,
    `username`            VARCHAR(255) NOT NULL DEFAULT '',
    `password_ciphertext` TEXT         NOT NULL,
    `url`                 VARCHAR(2048) NOT NULL DEFAULT '',
    `category`            VARCHAR(64)  NOT NULL DEFAULT '',
    `notes`               TEXT         NOT NULL,
    `created_at`          DATETIME(3)  NOT NULL,
    `updated_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_vault_entry_owner` (`owner_id`, `updated_at`),
    CONSTRAINT `fk_vault_entry_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户密码保险库条目';

CREATE TABLE `vault_unlock_session` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `owner_id`   BIGINT      NOT NULL,
    `token_hash` CHAR(64)    NOT NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `created_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vault_unlock_token` (`token_hash`),
    KEY `idx_vault_unlock_owner` (`owner_id`, `expires_at`),
    CONSTRAINT `fk_vault_unlock_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '密码保险库五分钟解锁会话';

CREATE TABLE `vault_passkey` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`              BIGINT       NOT NULL,
    `credential_id`         VARCHAR(512) NOT NULL,
    `registration_response` LONGTEXT     NOT NULL,
    `sign_count`            BIGINT       NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL,
    `last_used_at`          DATETIME(3)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vault_passkey_credential` (`credential_id`),
    KEY `idx_vault_passkey_owner` (`owner_id`, `created_at`),
    CONSTRAINT `fk_vault_passkey_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '密码保险库平台认证凭据';

CREATE TABLE `vault_webauthn_challenge` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`   BIGINT       NOT NULL,
    `purpose`    VARCHAR(24)  NOT NULL,
    `challenge`  VARCHAR(128) NOT NULL,
    `expires_at` DATETIME(3)  NOT NULL,
    `consumed_at` DATETIME(3) NULL,
    `created_at` DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_vault_challenge_owner` (
        `owner_id`,
        `purpose`,
        `consumed_at`,
        `expires_at`
    ),
    CONSTRAINT `fk_vault_challenge_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '密码保险库 WebAuthn 一次性挑战';
