package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.LocalDirectorySummary;

public interface LocalDirectoryInspector {

    long DEFAULT_LARGE_FILE_THRESHOLD_BYTES = 100L * 1024 * 1024;

    default LocalDirectorySummary inspect(String path) {
        return inspect(path, DEFAULT_LARGE_FILE_THRESHOLD_BYTES);
    }

    LocalDirectorySummary inspect(String path, long largeFileThresholdBytes);
}
