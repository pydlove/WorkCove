package com.pydlove.workcove.development.domain.model;

import java.util.List;

public record GitRepositoryStatistics(
        String since,
        String until,
        long totalCommits,
        long activeAuthors,
        long changedFiles,
        long additions,
        long deletions,
        long netLines,
        long totalLines,
        int trackedFiles,
        List<GitMonthStatistics> months,
        List<GitHourStatistics> hours,
        List<GitAuthorStatistics> authors) {
}
