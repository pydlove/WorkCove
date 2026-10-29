package com.pydlove.workcove.development.application.query;

import java.time.Instant;

public record ZentaoSyncResult(
        int projects,
        int bugs,
        int builds,
        Instant syncedAt
) {
}
