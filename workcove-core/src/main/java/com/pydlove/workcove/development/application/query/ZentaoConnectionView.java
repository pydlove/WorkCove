package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.model.ZentaoConnection;

import java.time.Instant;
import java.util.List;

public record ZentaoConnectionView(
        boolean configured,
        String baseUrl,
        String username,
        String password,
        List<String> projectIds,
        Instant lastSyncedAt
) {
    public static ZentaoConnectionView empty() {
        return new ZentaoConnectionView(
                false,
                "",
                "",
                "",
                List.of(),
                null);
    }

    public static ZentaoConnectionView from(
            ZentaoConnection connection,
            String password) {
        return new ZentaoConnectionView(
                true,
                connection.baseUrl(),
                connection.username(),
                password,
                List.copyOf(connection.projectIds()),
                connection.lastSyncedAt());
    }
}
