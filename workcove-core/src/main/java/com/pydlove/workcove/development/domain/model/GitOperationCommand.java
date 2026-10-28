package com.pydlove.workcove.development.domain.model;

public record GitOperationCommand(
        GitOperationType type,
        String branchName,
        String sourceBranch,
        String commitMessage,
        boolean force,
        boolean setUpstream) {
}
