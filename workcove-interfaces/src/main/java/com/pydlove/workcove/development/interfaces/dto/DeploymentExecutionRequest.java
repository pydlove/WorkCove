package com.pydlove.workcove.development.interfaces.dto;

public record DeploymentExecutionRequest(
        String workspaceId,
        Long serverId,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent) {
}
