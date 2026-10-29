package com.pydlove.workcove.development.domain.model;

import java.time.Instant;

public record ZentaoBug(
        Long id,
        long ownerId,
        String workspaceId,
        String remoteId,
        String projectId,
        String projectName,
        String productId,
        String productName,
        String title,
        String severity,
        String priority,
        String status,
        boolean confirmed,
        String steps,
        String openedBy,
        String openedDate,
        String assignedTo,
        boolean active,
        boolean localDeleted,
        boolean localResolved,
        String localStatus,
        String resolution,
        String solutionText,
        String reasonText,
        Instant pushedAt,
        Instant lastSyncedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
