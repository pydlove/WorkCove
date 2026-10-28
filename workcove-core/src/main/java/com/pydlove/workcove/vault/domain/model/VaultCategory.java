package com.pydlove.workcove.vault.domain.model;

import java.time.Instant;

public record VaultCategory(
        Long id,
        long ownerId,
        String name,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {
}
