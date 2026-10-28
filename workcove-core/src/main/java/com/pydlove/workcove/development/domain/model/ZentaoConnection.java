package com.pydlove.workcove.development.domain.model;

import java.time.Instant;
import java.util.List;

public record ZentaoConnection(
        Long id,
        long ownerId,
        String workspaceId,
        String baseUrl,
        String username,
        List<String> projectIds,
        String passwordCiphertext,
        Instant lastSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
