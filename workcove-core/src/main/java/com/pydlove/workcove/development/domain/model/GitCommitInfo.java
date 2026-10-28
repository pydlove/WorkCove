package com.pydlove.workcove.development.domain.model;

public record GitCommitInfo(
        String hash,
        String shortHash,
        String parents,
        String authorName,
        String authorEmail,
        String authoredAt,
        String subject) {
}
