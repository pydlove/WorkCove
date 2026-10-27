package com.pydlove.workcove.identity.interfaces.dto;

public record LoginRequest(
        String email,
        String password,
        boolean rememberMe) {
}
