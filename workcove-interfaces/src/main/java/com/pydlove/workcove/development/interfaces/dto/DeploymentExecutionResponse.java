package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.DeploymentExecutionResult;
import com.pydlove.workcove.development.domain.model.DeploymentExecutionStatus;

import java.time.Instant;

public record DeploymentExecutionResponse(
        DeploymentExecutionStatus status,
        Integer exitCode,
        String summary,
        String output,
        Instant startedAt,
        Instant finishedAt) {

    public static DeploymentExecutionResponse from(
            DeploymentExecutionResult result) {
        return new DeploymentExecutionResponse(
                result.status(),
                result.exitCode(),
                result.summary(),
                result.output(),
                result.startedAt(),
                result.finishedAt());
    }
}
