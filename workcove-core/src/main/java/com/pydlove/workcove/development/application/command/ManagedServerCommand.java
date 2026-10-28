package com.pydlove.workcove.development.application.command;

public record ManagedServerCommand(
        String name,
        String host,
        int port,
        String username,
        String password) {
}
