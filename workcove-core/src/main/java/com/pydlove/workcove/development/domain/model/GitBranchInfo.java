package com.pydlove.workcove.development.domain.model;

public record GitBranchInfo(
        String name,
        String refName,
        boolean current,
        boolean remote,
        String upstream,
        String latestCommit,
        String updatedAt) {
}
