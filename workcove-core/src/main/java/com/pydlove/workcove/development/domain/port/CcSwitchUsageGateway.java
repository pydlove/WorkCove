package com.pydlove.workcove.development.domain.port;

import java.time.Instant;

public interface CcSwitchUsageGateway {

    Usage read(Long fromEpochSeconds);

    record Usage(
            boolean available,
            String reason,
            long requestCount,
            long successCount,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheCreationTokens,
            long totalTokens,
            double costUsd,
            Double cacheHitRate,
            Instant updatedAt
    ) {
    }
}
