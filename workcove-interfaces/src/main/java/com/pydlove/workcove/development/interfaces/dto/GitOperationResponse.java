package com.pydlove.workcove.development.interfaces.dto;

import com.pydlove.workcove.development.domain.model.GitOperationResult;

import java.util.List;

public record GitOperationResponse(
        boolean success,
        String message,
        String output,
        String currentBranch,
        boolean mergeInProgress,
        List<String> conflictFiles) {

    public static GitOperationResponse from(GitOperationResult result) {
        return new GitOperationResponse(
                result.success(),
                result.message(),
                result.output(),
                result.currentBranch(),
                result.mergeInProgress(),
                result.conflictFiles());
    }
}
