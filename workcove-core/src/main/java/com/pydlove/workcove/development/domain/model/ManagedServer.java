package com.pydlove.workcove.development.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class ManagedServer {

    private final Long id;
    private final long ownerId;
    private final String workspaceId;
    private final String name;
    private final String host;
    private final int port;
    private final String username;
    private final String passwordCiphertext;
    private final String hostKeyAlgorithm;
    private final String hostKeyFingerprint;
    private final String hostKeyPublicKey;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ManagedServer(
            Long id,
            long ownerId,
            String workspaceId,
            String name,
            String host,
            int port,
            String username,
            String passwordCiphertext,
            String hostKeyAlgorithm,
            String hostKeyFingerprint,
            String hostKeyPublicKey,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.workspaceId = requireText(workspaceId, "工作空间", 64);
        this.name = requireText(name, "服务器名称", 80);
        this.host = requireText(host, "IP 或主机名", 255);
        if (port < 1 || port > 65535) {
            throw new DomainException("端口必须在 1 到 65535 之间");
        }
        this.port = port;
        this.username = requireText(username, "用户名", 128);
        this.passwordCiphertext = requireText(
                passwordCiphertext,
                "密码密文",
                4096);
        this.hostKeyAlgorithm = optionalText(hostKeyAlgorithm, 64);
        this.hostKeyFingerprint = optionalText(hostKeyFingerprint, 128);
        this.hostKeyPublicKey = optionalText(hostKeyPublicKey, 8192);
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间");
    }

    public static ManagedServer create(
            long ownerId,
            String workspaceId,
            String name,
            String host,
            int port,
            String username,
            String passwordCiphertext,
            Instant now) {
        return new ManagedServer(
                null,
                ownerId,
                workspaceId,
                name,
                host,
                port,
                username,
                passwordCiphertext,
                null,
                null,
                null,
                now,
                now);
    }

    public static ManagedServer restore(
            long id,
            long ownerId,
            String workspaceId,
            String name,
            String host,
            int port,
            String username,
            String passwordCiphertext,
            String hostKeyAlgorithm,
            String hostKeyFingerprint,
            String hostKeyPublicKey,
            Instant createdAt,
            Instant updatedAt) {
        return new ManagedServer(
                id,
                ownerId,
                workspaceId,
                name,
                host,
                port,
                username,
                passwordCiphertext,
                hostKeyAlgorithm,
                hostKeyFingerprint,
                hostKeyPublicKey,
                createdAt,
                updatedAt);
    }

    public ManagedServer update(
            String name,
            String host,
            int port,
            String username,
            String passwordCiphertext,
            Instant now) {
        return new ManagedServer(
                id,
                ownerId,
                workspaceId,
                name,
                host,
                port,
                username,
                passwordCiphertext,
                host.equals(this.host) && port == this.port
                        ? hostKeyAlgorithm
                        : null,
                host.equals(this.host) && port == this.port
                        ? hostKeyFingerprint
                        : null,
                host.equals(this.host) && port == this.port
                        ? hostKeyPublicKey
                        : null,
                createdAt,
                now);
    }

    public ManagedServer rememberHostKey(
            String algorithm,
            String fingerprint,
            String publicKey,
            Instant now) {
        return new ManagedServer(
                id,
                ownerId,
                workspaceId,
                name,
                host,
                port,
                username,
                passwordCiphertext,
                algorithm,
                fingerprint,
                publicKey,
                createdAt,
                now);
    }

    public ManagedServer copyToWorkspace(
            String targetWorkspaceId,
            String copyName,
            Instant now) {
        return new ManagedServer(
                null,
                ownerId,
                targetWorkspaceId,
                copyName,
                host,
                port,
                username,
                passwordCiphertext,
                hostKeyAlgorithm,
                hostKeyFingerprint,
                hostKeyPublicKey,
                now,
                now);
    }

    public ManagedServer moveToWorkspace(
            String targetWorkspaceId,
            Instant now) {
        return new ManagedServer(
                id,
                ownerId,
                targetWorkspaceId,
                name,
                host,
                port,
                username,
                passwordCiphertext,
                hostKeyAlgorithm,
                hostKeyFingerprint,
                hostKeyPublicKey,
                createdAt,
                now);
    }

    private static String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException(label + "不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainException("主机密钥信息过长");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public String getHostKeyAlgorithm() {
        return hostKeyAlgorithm;
    }

    public String getHostKeyFingerprint() {
        return hostKeyFingerprint;
    }

    public String getHostKeyPublicKey() {
        return hostKeyPublicKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
