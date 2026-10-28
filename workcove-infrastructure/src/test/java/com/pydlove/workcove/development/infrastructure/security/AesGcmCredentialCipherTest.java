package com.pydlove.workcove.development.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCredentialCipherTest {

    private final AesGcmCredentialCipher cipher =
            new AesGcmCredentialCipher("test-master-key");

    @Test
    void encryptsAndDecryptsWithoutStoringPlaintext() {
        String plaintext = "server-secret";

        String first = cipher.encrypt(plaintext);
        String second = cipher.encrypt(plaintext);

        assertTrue(first.startsWith("v1:"));
        assertFalse(first.contains(plaintext));
        assertNotEquals(first, second);
        assertEquals(plaintext, cipher.decrypt(first));
        assertEquals(plaintext, cipher.decrypt(second));
    }
}
