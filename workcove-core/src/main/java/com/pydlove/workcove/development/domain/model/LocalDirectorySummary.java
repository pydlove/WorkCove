package com.pydlove.workcove.development.domain.model;

public record LocalDirectorySummary(
        String path,
        long fileCount,
        long directoryCount,
        long totalBytes,
        long largeFileCount,
        long largeFileThresholdBytes,
        String lastModifiedAt,
        long inaccessibleCount,
        boolean truncated,
        long scanDurationMs
) {
}
