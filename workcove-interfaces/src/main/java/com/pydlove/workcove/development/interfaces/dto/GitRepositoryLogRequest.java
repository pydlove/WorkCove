package com.pydlove.workcove.development.interfaces.dto;

public record GitRepositoryLogRequest(
        String path,
        Integer limit) {
}
