package com.pydlove.workcove.identity.infrastructure.security;

import com.pydlove.workcove.identity.domain.model.EmailVerificationCodeHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureEmailVerificationCodecTest {

    private final SecureEmailVerificationCodec codec =
            new SecureEmailVerificationCodec();

    @Test
    void generatesSixDigitCodes() {
        assertTrue(codec.generateCode().matches("\\d{6}"));
    }

    @Test
    void hashesCodesDeterministicallyWithoutKeepingPlainText() {
        EmailVerificationCodeHash first = codec.hash("123456");
        EmailVerificationCodeHash second = codec.hash("123456");
        EmailVerificationCodeHash other = codec.hash("654321");

        assertEquals(first, second);
        assertNotEquals(first, other);
        assertTrue(first.value().matches("[0-9a-f]{64}"));
    }
}
