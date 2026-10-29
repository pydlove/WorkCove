package com.pydlove.workcove.development.application.command;

public record ZentaoBuildCreateCommand(
        String projectId,
        String executionId,
        String productId,
        String systemId,
        String name,
        String builder,
        String date,
        String downloadUrl
) {
}
