package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitReviewContext;

import java.util.List;

public record GitReviewContextResponse(
        String branch,
        String baseBranch,
        String baseCommit,
        String headCommit,
        String fromCommit,
        int commitCount,
        int fileCount,
        long additions,
        long deletions,
        List<GitCommitResponse> commits,
        List<String> changedFiles
) {
    public static GitReviewContextResponse from(
            GitReviewContext context) {
        return new GitReviewContextResponse(
                context.branch(),
                context.baseBranch(),
                context.baseCommit(),
                context.headCommit(),
                context.fromCommit(),
                context.commitCount(),
                context.fileCount(),
                context.additions(),
                context.deletions(),
                context.commits().stream()
                        .map(GitCommitResponse::from)
                        .toList(),
                context.changedFiles());
    }
}
