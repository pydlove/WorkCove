package com.pydlove.workcove.vault.domain.model;

import java.time.Instant;

public record VaultPasskey(
        Long id,
        long ownerId,
        String credentialId,
        String registrationResponse,
        long signCount,
        Instant createdAt,
        Instant lastUsedAt
) {
}
