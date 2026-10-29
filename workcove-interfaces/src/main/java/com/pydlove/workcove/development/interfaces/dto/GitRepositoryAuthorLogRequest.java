package com.pydlove.workcove.development.interfaces.dto;

public record GitRepositoryAuthorLogRequest(
        String path,
        String name,
        String email) {
}
