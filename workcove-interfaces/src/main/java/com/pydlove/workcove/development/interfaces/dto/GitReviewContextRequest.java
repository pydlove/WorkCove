package com.pydlove.workcove.development.interfaces.dto;

public record GitReviewContextRequest(
        String path,
        String branch,
        String baseBranch,
        String fromCommit,
        String since,
        String until
) {
}
