package com.pydlove.workcove.development.domain.model;

public record GitMonthStatistics(
        String month,
        long commits,
        long additions,
        long deletions) {
}
