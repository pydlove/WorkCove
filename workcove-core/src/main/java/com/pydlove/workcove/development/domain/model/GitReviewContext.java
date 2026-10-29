package com.pydlove.workcove.development.domain.model;

import java.util.List;

public record GitReviewContext(
        String branch,
        String baseBranch,
        String baseCommit,
        String headCommit,
        String fromCommit,
        int commitCount,
        int fileCount,
        long additions,
        long deletions,
        List<GitCommitInfo> commits,
        List<String> changedFiles
) {
}
