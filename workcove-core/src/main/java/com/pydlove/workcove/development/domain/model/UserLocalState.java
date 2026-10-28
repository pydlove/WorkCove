package com.pydlove.workcove.development.domain.model;

import java.time.Instant;

public record UserLocalState(
        Long id,
        long ownerId,
        String stateKey,
        String payload,
        Instant updatedAt
) {
}
