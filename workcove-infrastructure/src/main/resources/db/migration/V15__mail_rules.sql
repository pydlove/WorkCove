CREATE TABLE `mail_rule` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`            BIGINT       NOT NULL,
    `workspace_id`        VARCHAR(64)  NOT NULL,
    `account_id`          BIGINT       NOT NULL,
    `name`                VARCHAR(120) NOT NULL,
    `match_mode`          VARCHAR(8)   NOT NULL,
    `sender_contains`     VARCHAR(320) NOT NULL DEFAULT '',
    `subject_contains`    VARCHAR(500) NOT NULL DEFAULT '',
    `requires_attachment` BOOLEAN      NOT NULL DEFAULT FALSE,
    `target_folder`       VARCHAR(1024) NOT NULL,
    `enabled`             BOOLEAN      NOT NULL DEFAULT TRUE,
    `created_at`          DATETIME(3)  NOT NULL,
    `updated_at`          DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_mail_rule_account`
        (`owner_id`, `workspace_id`, `account_id`, `updated_at`),
    CONSTRAINT `fk_mail_rule_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_mail_rule_account`
        FOREIGN KEY (`account_id`) REFERENCES `mail_account` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '邮箱收信规则';
