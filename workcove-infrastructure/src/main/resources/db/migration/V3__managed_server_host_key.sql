ALTER TABLE `managed_server`
    ADD COLUMN `host_key_algorithm` VARCHAR(64) NULL AFTER `password_ciphertext`,
    ADD COLUMN `host_key_fingerprint` VARCHAR(128) NULL AFTER `host_key_algorithm`,
    ADD COLUMN `host_key_public_key` TEXT NULL AFTER `host_key_fingerprint`;
