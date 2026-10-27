package com.pydlove.workcove.boot.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigEncryptionTest {

    @Test
    void encryptsAndDecryptsWithAesGcm() {
        String plaintext = "smtp-password-value";
        String ciphertext = ConfigEncryption.encrypt(plaintext, "test-master-key");

        assertTrue(ciphertext.startsWith("ENC(v1:"));
        assertEquals(plaintext, ConfigEncryption.decrypt(ciphertext, "test-master-key"));
    }
}
