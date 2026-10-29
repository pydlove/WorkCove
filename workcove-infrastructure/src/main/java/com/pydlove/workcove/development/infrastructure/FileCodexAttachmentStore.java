package com.pydlove.workcove.development.infrastructure;

import com.pydlove.workcove.development.domain.model.CodexAttachment;
import com.pydlove.workcove.development.domain.port.CodexAttachmentStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FileCodexAttachmentStore implements CodexAttachmentStore {

    private static final Pattern ATTACHMENT_ID =
            Pattern.compile("[0-9a-fA-F-]{36}");

    private final Path root;
    private final Map<String, CodexAttachment> attachments =
            new ConcurrentHashMap<>();

    public FileCodexAttachmentStore() {
        this(Path.of(
                System.getProperty("java.io.tmpdir"),
                "workcove",
                "codex-attachments"));
    }

    public FileCodexAttachmentStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public CodexAttachment store(
            long ownerId,
            String workspaceId,
            String name,
            String contentType,
            long size,
            InputStream content) throws IOException {
        String id = UUID.randomUUID().toString();
        Path ownerDirectory = ownerDirectory(ownerId);
        Path workspaceDirectory = workspaceDirectory(
                ownerId,
                workspaceId);
        Files.createDirectories(workspaceDirectory);
        restrictDirectory(ownerDirectory);
        restrictDirectory(workspaceDirectory);
        Path directory = workspaceDirectory.resolve(id);
        Files.createDirectories(directory);
        restrictDirectory(directory);
        Path target = directory.resolve(name).normalize();
        if (!target.startsWith(directory)) {
            throw new IOException("附件路径无效");
        }
        try {
            Files.createFile(target);
            restrictFile(target);
            try (OutputStream output = Files.newOutputStream(target)) {
                content.transferTo(output);
            }
            long written = Files.size(target);
            if (written != size) {
                throw new IOException("附件写入不完整");
            }
            CodexAttachment attachment = new CodexAttachment(
                    id,
                    workspaceId,
                    name,
                    contentType,
                    written,
                    target.toAbsolutePath().normalize());
            attachments.put(key(ownerId, workspaceId, id), attachment);
            return attachment;
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    @Override
    public Optional<CodexAttachment> find(
            long ownerId,
            String workspaceId,
            String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) {
            return Optional.empty();
        }
        String safeId = attachmentId.trim();
        if (!ATTACHMENT_ID.matcher(safeId).matches()) {
            return Optional.empty();
        }
        CodexAttachment attachment =
                attachments.get(key(
                        ownerId,
                        workspaceId,
                        safeId));
        Path workspaceDirectory =
                workspaceDirectory(ownerId, workspaceId);
        if (attachment != null
                && attachment.path().startsWith(workspaceDirectory)) {
            return Optional.of(attachment);
        }
        Path directory = workspaceDirectory.resolve(safeId).normalize();
        if (!directory.startsWith(workspaceDirectory)
                || !Files.isDirectory(directory)
                || Files.isSymbolicLink(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(directory)) {
            Path file = files
                    .filter((path) -> Files.isRegularFile(path)
                            && !Files.isSymbolicLink(path))
                    .findFirst()
                    .orElse(null);
            if (file == null) return Optional.empty();
            String contentType = Files.probeContentType(file);
            CodexAttachment recovered = new CodexAttachment(
                    safeId,
                    workspaceId,
                    file.getFileName().toString(),
                    contentType == null || contentType.isBlank()
                            ? "application/octet-stream"
                            : contentType,
                    Files.size(file),
                    file.toAbsolutePath().normalize());
            attachments.put(
                    key(ownerId, workspaceId, safeId),
                    recovered);
            return Optional.of(recovered);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Path ownerDirectory(long ownerId) {
        return root.resolve(Long.toString(ownerId)).normalize();
    }

    private Path workspaceDirectory(
            long ownerId,
            String workspaceId) {
        Path workspace = Path.of(workspaceId);
        if (workspace.isAbsolute()
                || workspace.getNameCount() != 1
                || ".".equals(workspaceId)
                || "..".equals(workspaceId)) {
            throw new IllegalArgumentException("工作空间无效");
        }
        return ownerDirectory(ownerId)
                .resolve(workspace)
                .normalize();
    }

    private String key(
            long ownerId,
            String workspaceId,
            String attachmentId) {
        return ownerId + ":" + workspaceId + ":" + attachmentId;
    }

    private void restrictDirectory(Path path) {
        setPermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
    }

    private void restrictFile(Path path) {
        setPermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX hosts still benefit from the per-user directory layout.
        }
    }
}
