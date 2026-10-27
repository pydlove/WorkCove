package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    void normalizesOuterWhitespaceAndCase() {
        assertEquals(
                "developer@example.com",
                Email.of("  Developer@Example.COM  ").value());
    }

    @Test
    void rejectsMalformedAddress() {
        assertThrows(DomainException.class, () -> Email.of("not-an-email"));
    }
}
