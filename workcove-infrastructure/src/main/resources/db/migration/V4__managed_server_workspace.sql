ALTER TABLE `managed_server`
    ADD COLUMN `workspace_id` VARCHAR(64) NOT NULL DEFAULT 'default' AFTER `owner_id`,
    ADD KEY `idx_managed_server_workspace` (`owner_id`, `workspace_id`, `updated_at`);
