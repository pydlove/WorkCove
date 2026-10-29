ALTER TABLE `zentao_bug`
    ADD COLUMN `confirmed` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '禅道 Bug 是否已确认'
        AFTER `status`;
