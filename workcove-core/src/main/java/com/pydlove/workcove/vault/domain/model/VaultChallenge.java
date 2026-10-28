package com.pydlove.workcove.vault.domain.model;

import java.time.Instant;

public record VaultChallenge(
        Long id,
        long ownerId,
        String purpose,
        String challenge,
        Instant expiresAt,
        Instant consumedAt,
        Instant createdAt
) {
}
