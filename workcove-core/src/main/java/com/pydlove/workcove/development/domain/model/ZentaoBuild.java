package com.pydlove.workcove.development.domain.model;

import java.time.Instant;

public record ZentaoBuild(
        Long id,
        long ownerId,
        String workspaceId,
        String remoteId,
        String projectId,
        String projectName,
        String productId,
        String productName,
        String executionId,
        String name,
        String status,
        String builder,
        String buildDate,
        String description,
        String scmRevision,
        Instant lastSyncedAt,
        Instant createdAt,
        Instant updatedAt) {
}
