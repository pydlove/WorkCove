package com.pydlove.workcove.identity.application.command;

public record LoginCommand(
        String email,
        String password,
        boolean rememberMe) {
}
