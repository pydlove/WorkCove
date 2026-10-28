CREATE TABLE `user_local_state` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`    BIGINT       NOT NULL,
    `state_key`   VARCHAR(128) NOT NULL,
    `payload`     LONGTEXT     NOT NULL,
    `updated_at`  DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_local_state_key` (`owner_id`, `state_key`),
    CONSTRAINT `fk_user_local_state_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户业务状态 JSON';
