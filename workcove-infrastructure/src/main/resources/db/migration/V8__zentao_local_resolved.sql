ALTER TABLE `zentao_bug`
    ADD COLUMN `local_resolved` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '仅在 WorkCove 标记为已解决'
        AFTER `local_deleted`;
