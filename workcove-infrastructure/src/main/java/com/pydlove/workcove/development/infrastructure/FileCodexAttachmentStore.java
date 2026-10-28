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

public final class FileCodexAttachmentStore implements CodexAttachmentStore {

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
            String name,
            String contentType,
            long size,
            InputStream content) throws IOException {
        String id = UUID.randomUUID().toString();
        Path ownerDirectory = ownerDirectory(ownerId);
        Files.createDirectories(ownerDirectory);
        restrictDirectory(ownerDirectory);
        Path directory = ownerDirectory.resolve(id);
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
                    name,
                    contentType,
                    written,
                    target.toAbsolutePath().normalize());
            attachments.put(key(ownerId, id), attachment);
            return attachment;
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    @Override
    public Optional<CodexAttachment> find(long ownerId, String attachmentId) {
        if (attachmentId == null || attachmentId.isBlank()) {
            return Optional.empty();
        }
        CodexAttachment attachment =
                attachments.get(key(ownerId, attachmentId.trim()));
        if (attachment == null
                || !attachment.path().startsWith(ownerDirectory(ownerId))) {
            return Optional.empty();
        }
        return Optional.of(attachment);
    }

    private Path ownerDirectory(long ownerId) {
        return root.resolve(Long.toString(ownerId)).normalize();
    }

    private String key(long ownerId, String attachmentId) {
        return ownerId + ":" + attachmentId;
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
