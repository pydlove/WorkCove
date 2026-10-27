package com.pydlove.workcove.identity.infrastructure.security;

import com.pydlove.workcove.identity.domain.model.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void verifiesOnlyTheOriginalPassword() {
        PasswordHash hash = hasher.hash("correct-password");

        assertTrue(hasher.matches("correct-password", hash));
        assertFalse(hasher.matches("wrong-password", hash));
    }

    @Test
    void usesAUniqueSaltForEachHash() {
        PasswordHash first = hasher.hash("same-password");
        PasswordHash second = hasher.hash("same-password");

        assertFalse(first.value().equals(second.value()));
    }
}
