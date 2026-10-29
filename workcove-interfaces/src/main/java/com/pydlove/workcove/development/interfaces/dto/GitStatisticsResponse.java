package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitAuthorStatistics;
import com.pydlove.workcove.development.domain.model.GitHourStatistics;
import com.pydlove.workcove.development.domain.model.GitMonthStatistics;
import com.pydlove.workcove.development.domain.model.GitRepositoryStatistics;

import java.util.List;

public record GitStatisticsResponse(
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
        List<MonthResponse> months,
        List<HourResponse> hours,
        List<AuthorResponse> authors) {

    public static GitStatisticsResponse from(
            GitRepositoryStatistics statistics) {
        return new GitStatisticsResponse(
                statistics.since(),
                statistics.until(),
                statistics.totalCommits(),
                statistics.activeAuthors(),
                statistics.changedFiles(),
                statistics.additions(),
                statistics.deletions(),
                statistics.netLines(),
                statistics.totalLines(),
                statistics.trackedFiles(),
                statistics.months().stream()
                        .map(MonthResponse::from)
                        .toList(),
                statistics.hours().stream()
                        .map(HourResponse::from)
                        .toList(),
                statistics.authors().stream()
                        .map(AuthorResponse::from)
                        .toList());
    }

    public record MonthResponse(
            String month,
            long commits,
            long additions,
            long deletions) {

        public static MonthResponse from(GitMonthStatistics month) {
            return new MonthResponse(
                    month.month(),
                    month.commits(),
                    month.additions(),
                    month.deletions());
        }
    }

    public record HourResponse(
            int hour,
            long commits) {

        public static HourResponse from(GitHourStatistics hour) {
            return new HourResponse(hour.hour(), hour.commits());
        }
    }

    public record AuthorResponse(
            String name,
            String email,
            long commits,
            long additions,
            long deletions) {

        public static AuthorResponse from(GitAuthorStatistics author) {
            return new AuthorResponse(
                    author.name(),
                    author.email(),
                    author.commits(),
                    author.additions(),
                    author.deletions());
        }
    }
}
