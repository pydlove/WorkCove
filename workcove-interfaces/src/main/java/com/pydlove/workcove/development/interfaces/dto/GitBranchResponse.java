package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitBranchInfo;

public record GitBranchResponse(
        String name,
        String refName,
        boolean current,
        boolean remote,
        String upstream,
        String latestCommit,
        String updatedAt) {

    public static GitBranchResponse from(GitBranchInfo branch) {
        return new GitBranchResponse(
                branch.name(),
                branch.refName(),
                branch.current(),
                branch.remote(),
                branch.upstream(),
                branch.latestCommit(),
                branch.updatedAt());
    }
}
