package com.pydlove.workcove.development.application;

import com.pydlove.workcove.development.domain.model.ManagedServer;
import com.pydlove.workcove.development.domain.port.CredentialCipher;
import com.pydlove.workcove.development.domain.port.SftpGateway;
import com.pydlove.workcove.development.domain.port.SshTerminalGateway;
import com.pydlove.workcove.development.domain.repository.ManagedServerRepository;
import com.pydlove.workcove.shared.domain.DomainException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

public class SftpService {

    private static final long MAX_TRANSFER_BYTES = 200L * 1024 * 1024;

    private final ManagedServerRepository repository;
    private final CredentialCipher credentialCipher;
    private final SftpGateway gateway;
    private final Clock clock;

    public SftpService(
            ManagedServerRepository repository,
            CredentialCipher credentialCipher,
            SftpGateway gateway,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "服务器仓储");
        this.credentialCipher = Objects.requireNonNull(credentialCipher, "凭据加密器");
        this.gateway = Objects.requireNonNull(gateway, "SFTP 网关");
        this.clock = Objects.requireNonNull(clock, "时钟");
    }

    public List<SftpGateway.RemoteFile> list(
            long ownerId,
            String workspaceId,
            long serverId,
            String path) {
        String remotePath = normalizeRemotePath(path);
        SftpGateway.OpenedSftp opened = null;
        try {
            opened = open(ownerId, serverId, workspaceId);
            return opened.session().list(remotePath);
        } catch (IOException exception) {
            throw new DomainException("无法读取远程目录");
        } finally {
            closeQuietly(opened);
        }
    }

    public RemoteDownload download(
            long ownerId,
            String workspaceId,
            long serverId,
            String path) {
        String remotePath = normalizeRemotePath(path);
        SftpGateway.OpenedSftp opened = null;
        try {
            opened = open(ownerId, serverId, workspaceId);
            long remoteSize = opened.session().size(remotePath);
            if (remoteSize > MAX_TRANSFER_BYTES) {
                throw new DomainException("文件超过 200MB，暂不支持下载");
            }
            SftpGateway.DownloadedFile file = opened.session().download(remotePath);
            if (file.size() > MAX_TRANSFER_BYTES) {
                closeQuietly(file);
                throw new DomainException("文件超过 200MB，暂不支持下载");
            }
            SftpGateway.OpenedSftp owned = opened;
            return new RemoteDownload(
                    file.name(),
                    file.size(),
                    file.stream(),
                    () -> {
                        closeQuietly(file);
                        closeQuietly(owned);
                    });
        } catch (IOException exception) {
            closeQuietly(opened);
            throw new DomainException("无法下载远程文件");
        } catch (RuntimeException exception) {
            closeQuietly(opened);
            throw exception;
        }
    }

    public void upload(
            long ownerId,
            String workspaceId,
            long serverId,
            String directory,
            String filename,
            InputStream input,
            long size) {
        String remoteDirectory = normalizeRemotePath(directory);
        String safeFilename = requireFilename(filename);
        if (size > MAX_TRANSFER_BYTES) {
            throw new DomainException("文件超过 200MB，暂不支持上传");
        }
        SftpGateway.OpenedSftp opened = null;
        try {
            opened = open(ownerId, serverId, workspaceId);
            opened.session().upload(remoteDirectory, safeFilename, input, size);
        } catch (IOException exception) {
            throw new DomainException("无法上传文件");
        } finally {
            closeQuietly(opened);
        }
    }

    public void createDirectory(
            long ownerId,
            String workspaceId,
            long serverId,
            String path) {
        String remotePath = normalizeRemotePath(path);
        SftpGateway.OpenedSftp opened = null;
        try {
            opened = open(ownerId, serverId, workspaceId);
            opened.session().createDirectory(remotePath);
        } catch (IOException exception) {
            throw new DomainException("无法创建远程目录");
        } finally {
            closeQuietly(opened);
        }
    }

    public void delete(
            long ownerId,
            String workspaceId,
            long serverId,
            String path,
            boolean directory) {
        String remotePath = normalizeRemotePath(path);
        if ("/".equals(remotePath)) {
            throw new DomainException("不能删除根目录");
        }
        SftpGateway.OpenedSftp opened = null;
        try {
            opened = open(ownerId, serverId, workspaceId);
            opened.session().delete(remotePath, directory);
        } catch (IOException exception) {
            throw new DomainException("无法删除远程文件");
        } finally {
            closeQuietly(opened);
        }
    }

    private SftpGateway.OpenedSftp open(
            long ownerId,
            long serverId,
            String workspaceId) {
        ManagedServer server = repository
                .findByIdAndOwnerIdAndWorkspaceId(
                        serverId,
                        ownerId,
                        workspaceId)
                .orElseThrow(() -> new DomainException("服务器不存在或无权访问"));
        SshTerminalGateway.HostKey expected = server.getHostKeyPublicKey() == null
                ? null
                : new SshTerminalGateway.HostKey(
                        server.getHostKeyAlgorithm(),
                        server.getHostKeyFingerprint(),
                        server.getHostKeyPublicKey());
        SftpGateway.OpenedSftp opened = null;
        try {
            opened = gateway.open(
                    new SshTerminalGateway.Connection(
                            server.getHost(),
                            server.getPort(),
                            server.getUsername(),
                            credentialCipher.decrypt(server.getPasswordCiphertext())),
                    expected);
            if (expected == null
                    || !expected.publicKey().equals(
                            opened.hostKey().publicKey())) {
                repository.save(server.rememberHostKey(
                        opened.hostKey().algorithm(),
                        opened.hostKey().fingerprint(),
                        opened.hostKey().publicKey(),
                        clock.instant()));
            }
            return opened;
        } catch (IOException exception) {
            closeQuietly(opened);
            throw new DomainException("无法建立 SFTP 连接");
        } catch (RuntimeException exception) {
            closeQuietly(opened);
            throw exception;
        }
    }

    private static String normalizeRemotePath(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw new DomainException("远程路径无效");
        }
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            throw new DomainException("远程路径必须使用绝对路径");
        }
        return path.normalize().toString();
    }

    private static String requireFilename(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (value.isEmpty()
                || ".".equals(value)
                || "..".equals(value)
                || value.contains("/")
                || value.contains("\\")
                || value.indexOf('\0') >= 0) {
            throw new DomainException("文件名无效");
        }
        return value;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    public record RemoteDownload(
            String name,
            long size,
            InputStream stream,
            AutoCloseable cleanup) implements AutoCloseable {

        @Override
        public void close() {
            try {
                cleanup.close();
            } catch (Exception ignored) {
            }
        }
    }
}
