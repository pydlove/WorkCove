package com.pydlove.workcove.development.domain.port;

import java.time.Instant;
import java.util.List;

public interface SogouInputStatsGateway {

    Stats read();

    record DailyCount(String date, long count) {
    }

    record Stats(
            boolean installed,
            boolean serviceAvailable,
            String appPath,
            long totalInput,
            long currentSpeed,
            long historySpeed,
            Long historySpeedAt,
            List<DailyCount> days,
            String message,
            Instant checkedAt) {
    }
}
