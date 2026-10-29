package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.LocalDirectorySummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemDirectoryInspectorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void countsRegularFilesAndDirectoriesWithoutFollowingLinks()
            throws Exception {
        Path nested = Files.createDirectories(
                temporaryDirectory.resolve("nested"));
        Path first = Files.writeString(
                temporaryDirectory.resolve("first.txt"),
                "abc");
        Files.writeString(nested.resolve("second.txt"), "12345");
        Files.createSymbolicLink(
                temporaryDirectory.resolve("first-link.txt"),
                first);

        LocalDirectorySummary summary =
                new FileSystemDirectoryInspector().inspect(
                        temporaryDirectory.toString(),
                        4);

        assertEquals(2, summary.fileCount());
        assertEquals(1, summary.directoryCount());
        assertEquals(8, summary.totalBytes());
        assertEquals(1, summary.largeFileCount());
        assertEquals(4, summary.largeFileThresholdBytes());
        assertNotNull(summary.lastModifiedAt());
        assertEquals(0, summary.inaccessibleCount());
        assertFalse(summary.truncated());
    }

    @Test
    void marksResultsAsTruncatedWhenTheEntryLimitIsReached()
            throws Exception {
        Files.writeString(temporaryDirectory.resolve("first.txt"), "1");
        Files.writeString(temporaryDirectory.resolve("second.txt"), "2");

        LocalDirectorySummary summary =
                new FileSystemDirectoryInspector(1).inspect(
                        temporaryDirectory.toString());

        assertTrue(summary.truncated());
        assertEquals(1, summary.fileCount());
    }
}
