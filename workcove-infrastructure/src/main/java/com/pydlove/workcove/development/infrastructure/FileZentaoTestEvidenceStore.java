package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.port.ZentaoTestEvidenceStore;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FileZentaoTestEvidenceStore
        implements ZentaoTestEvidenceStore {

    private static final int MAX_EVIDENCE_FILES = 30;
    private static final long MAX_EVIDENCE_BYTES = 12L * 1024 * 1024;
    private static final Pattern SAFE_SEGMENT =
            Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final Path root;

    public FileZentaoTestEvidenceStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public PreparedRun prepare(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId) {
        Path directory = runDirectory(
                ownerId,
                workspaceId,
                bugId,
                runId);
        try {
            Files.createDirectories(directory);
            restrictDirectory(directory);
            return new PreparedRun(directory.toString());
        } catch (IOException exception) {
            throw new DomainException("无法创建测试证据目录");
        }
    }

    @Override
    public List<EvidenceSummary> list(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId) {
        Path directory = runDirectory(
                ownerId,
                workspaceId,
                bugId,
                runId);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(path -> Files.isRegularFile(
                            path,
                            LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> contentType(path) != null)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString()))
                    .limit(MAX_EVIDENCE_FILES)
                    .map(path -> {
                        String filename = path.getFileName().toString();
                        try {
                            return new EvidenceSummary(
                                    filename,
                                    contentType(path),
                                    Files.size(path));
                        } catch (IOException exception) {
                            throw new DomainException(
                                    "无法读取测试截图信息");
                        }
                    })
                    .toList();
        } catch (IOException exception) {
            throw new DomainException("无法读取测试截图目录");
        }
    }

    @Override
    public Optional<EvidenceFile> find(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId,
            String filename) {
        String safeFilename = safeFilename(filename);
        Path directory = runDirectory(
                ownerId,
                workspaceId,
                bugId,
                runId);
        Path file = directory.resolve(safeFilename).normalize();
        if (!file.startsWith(directory)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)) {
            return Optional.empty();
        }
        String contentType = contentType(file);
        if (contentType == null) return Optional.empty();
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_EVIDENCE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(new EvidenceFile(
                    safeFilename,
                    contentType,
                    size,
                    Files.readAllBytes(file)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Path runDirectory(
            long ownerId,
            String workspaceId,
            long bugId,
            String runId) {
        if (ownerId <= 0 || bugId <= 0) {
            throw new DomainException("测试证据范围无效");
        }
        Path ownerDirectory = root.resolve(
                Long.toString(ownerId)).normalize();
        Path directory = ownerDirectory
                .resolve(safeSegment(workspaceId, "工作空间"))
                .resolve(Long.toString(bugId))
                .resolve(safeSegment(runId, "测试批次"))
                .normalize();
        if (!directory.startsWith(ownerDirectory)) {
            throw new DomainException("测试证据目录无效");
        }
        return directory;
    }

    private String safeSegment(String value, String label) {
        String segment = value == null ? "" : value.trim();
        if (!SAFE_SEGMENT.matcher(segment).matches()
                || ".".equals(segment)
                || "..".equals(segment)) {
            throw new DomainException(label + "标识无效");
        }
        return segment;
    }

    private String safeFilename(String value) {
        String filename = value == null ? "" : value.trim();
        try {
            if (filename.isEmpty()
                    || filename.length() > 180
                    || !filename.equals(
                            Path.of(filename).getFileName().toString())) {
                throw new DomainException("测试截图文件名无效");
            }
        } catch (InvalidPathException exception) {
            throw new DomainException("测试截图文件名无效");
        }
        if (contentType(Path.of(filename)) == null) {
            throw new DomainException("只允许 png、jpg、jpeg 或 webp 测试截图");
        }
        return filename;
    }

    private String contentType(Path path) {
        String filename = path.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (filename.endsWith(".webp")) return "image/webp";
        return null;
    }

    private void restrictDirectory(Path path) {
        setPermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
    }

    private void setPermissions(
            Path path,
            Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX hosts still use the per-user directory layout.
        }
    }
}
