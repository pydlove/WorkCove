package com.pydlove.workcove.vault.domain.model;

import java.time.Instant;

public record VaultUnlockSession(
        Long id,
        long ownerId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt
) {
}
