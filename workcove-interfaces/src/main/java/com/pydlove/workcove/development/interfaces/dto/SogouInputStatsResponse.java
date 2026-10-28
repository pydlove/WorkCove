package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.port.SogouInputStatsGateway;

import java.time.Instant;
import java.util.List;

public record SogouInputStatsResponse(
        boolean installed,
        boolean serviceAvailable,
        String appPath,
        long totalInput,
        long currentSpeed,
        long historySpeed,
        Long historySpeedAt,
        List<SogouInputStatsGateway.DailyCount> days,
        String message,
        Instant checkedAt) {

    public static SogouInputStatsResponse from(
            SogouInputStatsGateway.Stats stats) {
        return new SogouInputStatsResponse(
                stats.installed(),
                stats.serviceAvailable(),
                stats.appPath(),
                stats.totalInput(),
                stats.currentSpeed(),
                stats.historySpeed(),
                stats.historySpeedAt(),
                stats.days(),
                stats.message(),
                stats.checkedAt());
    }
}
