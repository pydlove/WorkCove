package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.application.query.ManagedServerView;

import java.time.Instant;

public record ManagedServerResponse(
        long id,
        String workspaceId,
        String name,
        String host,
        int port,
        String username,
        String hostKeyFingerprint,
        Instant createdAt,
        Instant updatedAt) {

    public static ManagedServerResponse from(ManagedServerView view) {
        return new ManagedServerResponse(
                view.id(),
                view.workspaceId(),
                view.name(),
                view.host(),
                view.port(),
                view.username(),
                view.hostKeyFingerprint(),
                view.createdAt(),
                view.updatedAt());
    }
}
