CREATE TABLE `mail_folder` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`     BIGINT       NOT NULL,
    `workspace_id` VARCHAR(64)  NOT NULL,
    `account_id`   BIGINT       NOT NULL,
    `name`         VARCHAR(255) NOT NULL,
    `path`         VARCHAR(512) NOT NULL,
    `role`         VARCHAR(16)  NOT NULL,
    `unread_count` INT          NOT NULL DEFAULT 0,
    `total_count`  INT          NOT NULL DEFAULT 0,
    `synced_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mail_folder_account_path` (`account_id`, `path`),
    KEY `idx_mail_folder_scope`
        (`owner_id`, `workspace_id`, `account_id`, `synced_at`),
    CONSTRAINT `fk_mail_folder_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_mail_folder_account`
        FOREIGN KEY (`account_id`) REFERENCES `mail_account` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '邮箱目录同步缓存';

CREATE TABLE `mail_message` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `folder_id`         BIGINT       NOT NULL,
    `uid`               VARCHAR(64)  NOT NULL,
    `message_id`        VARCHAR(998) NOT NULL DEFAULT '',
    `from_name`         VARCHAR(320) NOT NULL DEFAULT '',
    `from_address`      VARCHAR(320) NOT NULL DEFAULT '',
    `to_recipients`     JSON         NOT NULL,
    `cc_recipients`     JSON         NOT NULL,
    `subject`           VARCHAR(1000) NOT NULL DEFAULT '',
    `received_at`       DATETIME(3)  NOT NULL,
    `preview`           VARCHAR(1000) NOT NULL DEFAULT '',
    `content`           LONGTEXT     NOT NULL,
    `html`              BOOLEAN      NOT NULL DEFAULT FALSE,
    `unread`            BOOLEAN      NOT NULL DEFAULT TRUE,
    `starred`           BOOLEAN      NOT NULL DEFAULT FALSE,
    `attachment_count`  INT          NOT NULL DEFAULT 0,
    `attachments`       JSON         NOT NULL,
    `synced_at`         DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mail_message_folder_uid` (`folder_id`, `uid`),
    KEY `idx_mail_message_list`
        (`folder_id`, `received_at`, `uid`),
    KEY `idx_mail_message_flagged`
        (`folder_id`, `starred`, `received_at`),
    CONSTRAINT `fk_mail_message_folder`
        FOREIGN KEY (`folder_id`) REFERENCES `mail_folder` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '邮箱邮件同步缓存';
