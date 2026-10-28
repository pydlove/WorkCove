package com.pydlove.workcove.development.application.query;

import com.pydlove.workcove.development.domain.model.ManagedServer;

import java.time.Instant;

public record ManagedServerView(
        long id,
        String workspaceId,
        String name,
        String host,
        int port,
        String username,
        String hostKeyFingerprint,
        Instant createdAt,
        Instant updatedAt) {

    public static ManagedServerView from(ManagedServer server) {
        return new ManagedServerView(
                server.getId(),
                server.getWorkspaceId(),
                server.getName(),
                server.getHost(),
                server.getPort(),
                server.getUsername(),
                server.getHostKeyFingerprint(),
                server.getCreatedAt(),
                server.getUpdatedAt());
    }
}
