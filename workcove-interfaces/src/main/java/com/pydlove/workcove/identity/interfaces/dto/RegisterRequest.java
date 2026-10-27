package com.pydlove.workcove.identity.interfaces.dto;

public record RegisterRequest(
        String email,
        String emailCode,
        String password,
        String confirmPassword) {
}
