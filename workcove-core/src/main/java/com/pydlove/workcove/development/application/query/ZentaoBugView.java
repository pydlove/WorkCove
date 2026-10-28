package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.model.ZentaoBug;

import java.time.Instant;

public record ZentaoBugView(
        long id,
        String remoteId,
        String projectId,
        String projectName,
        String productId,
        String productName,
        String title,
        String severity,
        String priority,
        String status,
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
        Instant lastSyncedAt
) {
    public static ZentaoBugView from(ZentaoBug bug) {
        return new ZentaoBugView(
                bug.id(),
                bug.remoteId(),
                bug.projectId(),
                bug.projectName(),
                bug.productId(),
                bug.productName(),
                bug.title(),
                bug.severity(),
                bug.priority(),
                bug.status(),
                bug.steps(),
                bug.openedBy(),
                bug.openedDate(),
                bug.assignedTo(),
                bug.active(),
                bug.localDeleted(),
                bug.localResolved(),
                bug.localStatus(),
                bug.resolution(),
                bug.solutionText(),
                bug.reasonText(),
                bug.pushedAt(),
                bug.lastSyncedAt());
    }
}
