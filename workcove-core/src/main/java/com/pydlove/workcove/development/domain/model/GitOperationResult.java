package com.pydlove.workcove.development.domain.model;

import java.util.List;

public record GitOperationResult(
        boolean success,
        String message,
        String output,
        String currentBranch,
        boolean mergeInProgress,
        List<String> conflictFiles) {
}
