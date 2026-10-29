package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.LocalDirectorySummary;
import com.pydlove.workcove.development.domain.port.LocalDirectoryInspector;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

public final class FileSystemDirectoryInspector
        implements LocalDirectoryInspector {

    static final long DEFAULT_MAX_ENTRIES = 300_000;

    private final long maxEntries;

    public FileSystemDirectoryInspector() {
        this(DEFAULT_MAX_ENTRIES);
    }

    FileSystemDirectoryInspector(long maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("目录扫描上限必须大于 0");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public LocalDirectorySummary inspect(
            String rawPath,
            long largeFileThresholdBytes) {
        if (largeFileThresholdBytes < 1) {
            throw new DomainException("大文件阈值必须大于 0");
        }
        Path root = MacDirectoryPicker.validateDirectory(rawPath);
        long startedAt = System.nanoTime();
        Counter counter = new Counter();
        boolean truncated = false;
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path directory,
                                BasicFileAttributes attributes) throws IOException {
                            if (!directory.equals(root)) {
                                counter.enter();
                                counter.directoryCount++;
                                counter.observeModified(attributes.lastModifiedTime()
                                        .toMillis());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file,
                                BasicFileAttributes attributes) throws IOException {
                            counter.enter();
                            if (attributes.isRegularFile()) {
                                counter.fileCount++;
                                long fileSize = Math.max(
                                        0,
                                        attributes.size());
                                counter.totalBytes += fileSize;
                                if (fileSize >= largeFileThresholdBytes) {
                                    counter.largeFileCount++;
                                }
                                counter.observeModified(attributes.lastModifiedTime()
                                        .toMillis());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(
                                Path file,
                                IOException exception) {
                            counter.inaccessibleCount++;
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (ScanLimitReachedException exception) {
            truncated = true;
        } catch (IOException exception) {
            throw new DomainException("无法读取目录内容", exception);
        }

        long duration = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
        return new LocalDirectorySummary(
                root.toString(),
                counter.fileCount,
                counter.directoryCount,
                counter.totalBytes,
                counter.largeFileCount,
                largeFileThresholdBytes,
                counter.lastModifiedAt > 0
                        ? java.time.Instant.ofEpochMilli(
                                counter.lastModifiedAt).toString()
                        : null,
                counter.inaccessibleCount,
                truncated,
                Math.max(0, duration));
    }

    private final class Counter {
        private long entries;
        private long fileCount;
        private long directoryCount;
        private long totalBytes;
        private long largeFileCount;
        private long inaccessibleCount;
        private long lastModifiedAt;

        private void enter() throws ScanLimitReachedException {
            entries++;
            if (entries > maxEntries) {
                throw new ScanLimitReachedException();
            }
        }

        private void observeModified(long value) {
            if (value > lastModifiedAt) lastModifiedAt = value;
        }
    }

    private static final class ScanLimitReachedException
            extends IOException {
    }
}
