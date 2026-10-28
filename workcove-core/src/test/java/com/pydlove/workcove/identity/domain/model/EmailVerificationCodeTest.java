package com.pydlove.workcove.identity.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationCodeTest {

    private static final Email EMAIL = Email.of("developer@example.com");
    private static final EmailVerificationCodeHash HASH =
            new EmailVerificationCodeHash("a".repeat(64));

    @Test
    void verifiesAndConsumesOnce() {
        Instant now = Instant.parse("2026-10-27T00:00:00Z");
        EmailVerificationCode code = EmailVerificationCode.issue(
                EMAIL,
                HASH,
                now,
                Duration.ofMinutes(5));

        assertTrue(code.verify(HASH, now.plusSeconds(30)));
        assertFalse(code.isActive(now.plusSeconds(31)));
        assertFalse(code.verify(HASH, now.plusSeconds(31)));
    }

    @Test
    void invalidatesAfterFiveFailedAttempts() {
        Instant now = Instant.parse("2026-10-27T00:00:00Z");
        EmailVerificationCodeHash wrong =
                new EmailVerificationCodeHash("b".repeat(64));
        EmailVerificationCode code = EmailVerificationCode.issue(
                EMAIL,
                HASH,
                now,
                Duration.ofMinutes(5));

        for (int attempt = 0; attempt < 4; attempt += 1) {
            assertFalse(code.verify(wrong, now.plusSeconds(attempt)));
        }
        assertTrue(code.isActive(now.plusSeconds(5)));
        assertFalse(code.verify(wrong, now.plusSeconds(5)));
        assertFalse(code.isActive(now.plusSeconds(6)));
    }
}
