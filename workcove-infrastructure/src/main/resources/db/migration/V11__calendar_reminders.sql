CREATE TABLE `calendar_event` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`                BIGINT       NOT NULL,
    `workspace_id`            VARCHAR(64)  NOT NULL,
    `event_key`               CHAR(36)     NOT NULL,
    `title`                   VARCHAR(120) NOT NULL,
    `event_date`              DATE         NOT NULL,
    `start_time`              TIME         NOT NULL,
    `end_time`                TIME         NULL,
    `notes`                   TEXT         NOT NULL,
    `color`                   VARCHAR(16)  NOT NULL,
    `reminder_in_app`         BOOLEAN      NOT NULL DEFAULT TRUE,
    `reminder_email`          BOOLEAN      NOT NULL DEFAULT FALSE,
    `reminder_minutes_before` INT          NOT NULL DEFAULT 10,
    `created_at`              DATETIME(3)  NOT NULL,
    `updated_at`              DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_calendar_event_key` (`owner_id`, `event_key`),
    KEY `idx_calendar_event_owner_date` (
        `owner_id`,
        `workspace_id`,
        `event_date`,
        `start_time`
    ),
    CONSTRAINT `fk_calendar_event_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户日程';

CREATE TABLE `calendar_reminder_settings` (
    `owner_id`               BIGINT      NOT NULL,
    `default_in_app`         BOOLEAN     NOT NULL DEFAULT TRUE,
    `default_email`          BOOLEAN     NOT NULL DEFAULT FALSE,
    `default_minutes_before` INT         NOT NULL DEFAULT 10,
    `updated_at`             DATETIME(3) NOT NULL,
    PRIMARY KEY (`owner_id`),
    CONSTRAINT `fk_calendar_reminder_settings_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '日程提醒默认设置';

CREATE TABLE `calendar_reminder` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`         BIGINT       NOT NULL,
    `event_id`         BIGINT       NOT NULL,
    `channel`          VARCHAR(16)  NOT NULL,
    `scheduled_at_utc` DATETIME(3)  NOT NULL,
    `available_at_utc` DATETIME(3)  NOT NULL,
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `attempts`         INT          NOT NULL DEFAULT 0,
    `sent_at_utc`      DATETIME(3)  NULL,
    `last_error`       VARCHAR(1000) NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    `updated_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_calendar_reminder_delivery` (
        `event_id`,
        `channel`,
        `scheduled_at_utc`
    ),
    KEY `idx_calendar_reminder_due` (
        `status`,
        `available_at_utc`,
        `scheduled_at_utc`
    ),
    KEY `idx_calendar_reminder_owner` (`owner_id`, `created_at`),
    CONSTRAINT `fk_calendar_reminder_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_calendar_reminder_event`
        FOREIGN KEY (`event_id`) REFERENCES `calendar_event` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '日程提醒投递任务';

CREATE TABLE `in_app_notification` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `owner_id`    BIGINT        NOT NULL,
    `workspace_id` VARCHAR(64)  NOT NULL,
    `event_id`    BIGINT        NULL,
    `title`       VARCHAR(160)  NOT NULL,
    `body`        VARCHAR(2000) NOT NULL,
    `link_view`   VARCHAR(64)   NOT NULL DEFAULT '',
    `dedupe_key`  VARCHAR(191)  NOT NULL,
    `read_at_utc` DATETIME(3)   NULL,
    `created_at`  DATETIME(3)   NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_in_app_notification_dedupe` (`owner_id`, `dedupe_key`),
    KEY `idx_in_app_notification_owner` (
        `owner_id`,
        `read_at_utc`,
        `created_at`
    ),
    CONSTRAINT `fk_in_app_notification_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_in_app_notification_event`
        FOREIGN KEY (`event_id`) REFERENCES `calendar_event` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '工作台站内信通知';
