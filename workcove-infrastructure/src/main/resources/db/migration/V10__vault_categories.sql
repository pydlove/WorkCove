CREATE TABLE `vault_category` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `owner_id`   BIGINT      NOT NULL,
    `name`       VARCHAR(64) NOT NULL,
    `sort_order` INT         NOT NULL,
    `created_at` DATETIME(3) NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vault_category_owner_name` (`owner_id`, `name`),
    KEY `idx_vault_category_owner_order` (`owner_id`, `sort_order`),
    CONSTRAINT `fk_vault_category_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户密码保险库分类';

UPDATE `vault_entry`
SET `category` = '未分类'
WHERE `category` = '';

INSERT IGNORE INTO `vault_category` (
    `owner_id`,
    `name`,
    `sort_order`,
    `created_at`,
    `updated_at`
)
SELECT
    `id`,
    '未分类',
    0,
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3)
FROM `app_user`;

INSERT IGNORE INTO `vault_category` (
    `owner_id`,
    `name`,
    `sort_order`,
    `created_at`,
    `updated_at`
)
SELECT
    `categorized`.`owner_id`,
    `categorized`.`category`,
    ROW_NUMBER() OVER (
        PARTITION BY `categorized`.`owner_id`
        ORDER BY `categorized`.`latest_updated_at` DESC
    ),
    `categorized`.`first_created_at`,
    `categorized`.`latest_updated_at`
FROM (
    SELECT
        `owner_id`,
        `category`,
        MIN(`created_at`) AS `first_created_at`,
        MAX(`updated_at`) AS `latest_updated_at`
    FROM `vault_entry`
    WHERE `category` <> '' AND `category` <> '未分类'
    GROUP BY `owner_id`, `category`
) AS `categorized`;
