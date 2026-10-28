package com.pydlove.workcove.development.application.query;

import java.time.Instant;

public record ZentaoSyncResult(
        int projects,
        int bugs,
        Instant syncedAt
) {
}
