package com.pydlove.workcove.development.application.command;

public record DeploymentExecutionCommand(
        String workspaceId,
        Long serverId,
        String remoteDirectory,
        String scriptFilename,
        String scriptContent) {
}
