ALTER TABLE `calendar_reminder_settings`
    ADD COLUMN `workspace_id` VARCHAR(64) NOT NULL DEFAULT 'default'
        AFTER `owner_id`,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`owner_id`, `workspace_id`);
