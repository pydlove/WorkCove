package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.model.ZentaoBuild;

public record ZentaoBuildView(
        long id,
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
        String lastSyncedAt) {

    public static ZentaoBuildView from(ZentaoBuild build) {
        return new ZentaoBuildView(
                build.id(),
                build.remoteId(),
                build.projectId(),
                build.projectName(),
                build.productId(),
                build.productName(),
                build.executionId(),
                build.name(),
                build.status(),
                build.builder(),
                build.buildDate(),
                build.description(),
                build.scmRevision(),
                build.lastSyncedAt().toString());
    }
}
