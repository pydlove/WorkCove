package com.pydlove.workcove.development.interfaces.dto;

public record ManagedServerRequest(
        String workspaceId,
        String name,
        String host,
        int port,
        String username,
        String password) {
}
