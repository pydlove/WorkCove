package com.pydlove.workcove.development.domain.model;

public record GitAuthorStatistics(
        String name,
        String email,
        long commits,
        long additions,
        long deletions) {
}
