package com.pydlove.workcove.development.domain.model;

import java.time.Instant;

public record DeploymentExecutionResult(
        DeploymentExecutionStatus status,
        Integer exitCode,
        String summary,
        String output,
        Instant startedAt,
        Instant finishedAt) {
}
