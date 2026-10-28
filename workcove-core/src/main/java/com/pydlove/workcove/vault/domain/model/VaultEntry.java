package com.pydlove.workcove.vault.domain.model;

import java.time.Instant;

public record VaultEntry(
        Long id,
        long ownerId,
        String name,
        String username,
        String passwordCiphertext,
        String url,
        String category,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
