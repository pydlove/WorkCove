ALTER TABLE `zentao_connection`
    ADD COLUMN `project_ids` TEXT NULL
        COMMENT '选择同步的禅道项目 ID JSON 数组';

UPDATE `zentao_connection`
SET `project_ids` = '[]'
WHERE `project_ids` IS NULL;

ALTER TABLE `zentao_connection`
    MODIFY COLUMN `project_ids` TEXT NOT NULL
        COMMENT '选择同步的禅道项目 ID JSON 数组';

ALTER TABLE `zentao_bug`
    ADD COLUMN `project_id` VARCHAR(64) NOT NULL DEFAULT ''
        COMMENT '禅道项目 ID' AFTER `remote_id`,
    ADD COLUMN `project_name` VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '禅道项目名称' AFTER `project_id`,
    ADD COLUMN `local_deleted` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '仅从 WorkCove 隐藏，不删除禅道数据'
        AFTER `active`;
