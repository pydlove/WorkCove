ALTER TABLE `vault_entry`
    ADD COLUMN `workspace_id` VARCHAR(64) NOT NULL DEFAULT 'default'
        AFTER `owner_id`,
    DROP INDEX `idx_vault_entry_owner`,
    ADD KEY `idx_vault_entry_workspace`
        (`owner_id`, `workspace_id`, `updated_at`);

ALTER TABLE `vault_category`
    ADD COLUMN `workspace_id` VARCHAR(64) NOT NULL DEFAULT 'default'
        AFTER `owner_id`,
    DROP INDEX `uk_vault_category_owner_name`,
    DROP INDEX `idx_vault_category_owner_order`,
    ADD UNIQUE KEY `uk_vault_category_workspace_name`
        (`owner_id`, `workspace_id`, `name`),
    ADD KEY `idx_vault_category_workspace_order`
        (`owner_id`, `workspace_id`, `sort_order`);
