ALTER TABLE `mail_account`
    ADD COLUMN `smtp_auth` BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT 'SMTP 是否使用账号认证'
        AFTER `smtp_ssl`;
