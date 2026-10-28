package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitOperationCommand;
import com.pydlove.workcove.development.domain.model.GitOperationType;

public record GitRepositoryOperationRequest(
        String path,
        GitOperationType type,
        String branchName,
        String sourceBranch,
        String commitMessage,
        boolean force,
        boolean setUpstream) {

    public GitOperationCommand toCommand() {
        return new GitOperationCommand(
                type,
                branchName,
                sourceBranch,
                commitMessage,
                force,
                setUpstream);
    }
}
