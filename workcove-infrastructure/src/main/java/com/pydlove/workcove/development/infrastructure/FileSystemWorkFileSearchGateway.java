package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.WorkFileMatch;
import com.pydlove.workcove.development.domain.port.WorkFileSearchGateway;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class FileSystemWorkFileSearchGateway
        implements WorkFileSearchGateway {

    private static final int MAX_RESULTS = 8;
    private static final int MAX_MATCHES = 200;
    private static final int MAX_VISITED_ENTRIES = 20_000;
    private static final int MAX_DEPTH = 8;
    private static final long SEARCH_TIMEOUT_NANOS =
            TimeUnit.MILLISECONDS.toNanos(3_500);
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git",
            ".idea",
            ".next",
            ".nuxt",
            ".output",
            ".turbo",
            ".venv",
            ".yarn",
            "build",
            "coverage",
            "dist",
            "node_modules",
            "out",
            "target",
            "vendor");

    @Override
    public List<WorkFileMatch> search(List<String> roots, String query) {
        String normalizedQuery = normalizeSearchText(query);
        if (normalizedQuery.isEmpty()) return List.of();

        long deadline = System.nanoTime() + SEARCH_TIMEOUT_NANOS;
        List<Candidate> candidates = new ArrayList<>();
        int[] visitedEntries = {0};

        for (Path root : realDirectories(roots)) {
            if (System.nanoTime() >= deadline
                    || candidates.size() >= MAX_MATCHES) {
                break;
            }
            String rootName = root.getFileName() == null
                    ? root.toString()
                    : root.getFileName().toString();
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory,
                            BasicFileAttributes attributes) {
                        if (directory.equals(root)) {
                            return FileVisitResult.CONTINUE;
                        }
                        String name = directory.getFileName().toString();
                        int depth = root.relativize(directory).getNameCount();
                        if (name.startsWith(".")
                                || IGNORED_DIRECTORIES.contains(name)
                                || depth >= MAX_DEPTH) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return shouldStop(deadline, visitedEntries[0],
                                candidates.size())
                                ? FileVisitResult.TERMINATE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes) {
                        visitedEntries[0] += 1;
                        if (shouldStop(deadline, visitedEntries[0],
                                candidates.size())) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (!attributes.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }
                        String relativePath = root.relativize(file).toString();
                        String searchable = normalizeSearchText(
                                file.getFileName() + "\n" + relativePath);
                        if (!searchable.contains(normalizedQuery)) {
                            return FileVisitResult.CONTINUE;
                        }
                        candidates.add(new Candidate(
                                new WorkFileMatch(
                                        file.getFileName().toString(),
                                        file.toString(),
                                        rootName,
                                        relativePath),
                                resultScore(
                                        file.getFileName().toString(),
                                        relativePath,
                                        normalizedQuery)));
                        return candidates.size() >= MAX_MATCHES
                                ? FileVisitResult.TERMINATE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file,
                            IOException exception) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | SecurityException ignored) {
                // Continue with the remaining configured directories.
            }
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingInt(Candidate::score)
                        .thenComparingInt(candidate ->
                                candidate.match().relativePath().length())
                        .thenComparing(
                                candidate ->
                                        candidate.match().relativePath(),
                                String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .map(Candidate::match)
                .toList();
    }

    @Override
    public void openFile(String path, List<String> roots) {
        requireMac();
        Path file = validateFile(path, roots);
        try {
            Process process = new ProcessBuilder(
                    "/usr/bin/open",
                    file.toString())
                    .redirectErrorStream(true)
                    .start();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("打开文件超时");
            }
            if (process.exitValue() != 0) {
                String message = new String(output, StandardCharsets.UTF_8)
                        .trim();
                throw new DomainException(message.isEmpty()
                        ? "无法使用默认应用打开文件"
                        : message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("打开文件已中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法使用默认应用打开文件", exception);
        }
    }

    static Path validateFile(String rawPath, List<String> roots) {
        Path file;
        try {
            file = Path.of(rawPath).toRealPath();
        } catch (IOException | RuntimeException exception) {
            throw new DomainException("文件不存在或无法访问", exception);
        }
        if (!Files.isRegularFile(file)) {
            throw new DomainException("目标不是文件");
        }
        for (Path root : realDirectories(roots)) {
            if (file.startsWith(root) && !file.equals(root)) {
                return file;
            }
        }
        throw new DomainException("文件不在已配置的工作目录中");
    }

    private static List<Path> realDirectories(List<String> roots) {
        Set<Path> directories = new HashSet<>();
        for (String rawRoot : roots) {
            try {
                Path directory = Path.of(rawRoot).toRealPath();
                if (Files.isDirectory(directory)) {
                    directories.add(directory);
                }
            } catch (IOException | RuntimeException ignored) {
                // Ignore invalid roots and keep the other configured folders.
            }
        }
        return List.copyOf(directories);
    }

    private static boolean shouldStop(
            long deadline,
            int visitedEntries,
            int matches) {
        return System.nanoTime() >= deadline
                || visitedEntries >= MAX_VISITED_ENTRIES
                || matches >= MAX_MATCHES;
    }

    private static int resultScore(
            String name,
            String relativePath,
            String query) {
        String normalizedName = normalizeSearchText(name);
        String normalizedPath = normalizeSearchText(relativePath);
        if (normalizedName.equals(query)) return 0;
        if (normalizedName.startsWith(query)) return 1;
        if (normalizedName.contains(query)) return 2;
        return normalizedPath.contains(query) ? 3 : 4;
    }

    private static String normalizeSearchText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-_·]+", "");
    }

    private static void requireMac() {
        if (!System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac")) {
            throw new DomainException("当前系统暂不支持默认应用打开文件");
        }
    }

    private record Candidate(WorkFileMatch match, int score) {
    }
}
