package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitCommitInfo;

public record GitCommitResponse(
        String hash,
        String shortHash,
        String parents,
        String authorName,
        String authorEmail,
        String authoredAt,
        String subject) {

    public static GitCommitResponse from(GitCommitInfo commit) {
        return new GitCommitResponse(
                commit.hash(),
                commit.shortHash(),
                commit.parents(),
                commit.authorName(),
                commit.authorEmail(),
                commit.authoredAt(),
                commit.subject());
    }
}
