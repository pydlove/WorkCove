package com.pydlove.workcove.database.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class DatabaseConnection {

    private final Long id;
    private final long ownerId;
    private final String workspaceId;
    private final String name;
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String passwordCiphertext;
    private final boolean sslEnabled;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DatabaseConnection(
            Long id,
            long ownerId,
            String workspaceId,
            String name,
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String passwordCiphertext,
            boolean sslEnabled,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.workspaceId = required(workspaceId, "工作空间", 64);
        this.name = required(name, "连接名称", 80);
        this.type = Objects.requireNonNull(type, "数据库类型");
        this.host = host(host);
        if (port < 1 || port > 65535) {
            throw new DomainException("端口必须在 1 到 65535 之间");
        }
        this.port = port;
        this.databaseName = optional(databaseName, "数据库名", 128);
        this.username = required(username, "用户名", 128);
        this.passwordCiphertext = required(
                passwordCiphertext,
                "密码密文",
                4096);
        this.sslEnabled = sslEnabled;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间");
    }

    public static DatabaseConnection create(
            long ownerId,
            String workspaceId,
            String name,
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String passwordCiphertext,
            boolean sslEnabled,
            Instant now) {
        return new DatabaseConnection(
                null,
                ownerId,
                workspaceId,
                name,
                type,
                host,
                port,
                databaseName,
                username,
                passwordCiphertext,
                sslEnabled,
                now,
                now);
    }

    public static DatabaseConnection restore(
            long id,
            long ownerId,
            String workspaceId,
            String name,
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String passwordCiphertext,
            boolean sslEnabled,
            Instant createdAt,
            Instant updatedAt) {
        return new DatabaseConnection(
                id,
                ownerId,
                workspaceId,
                name,
                type,
                host,
                port,
                databaseName,
                username,
                passwordCiphertext,
                sslEnabled,
                createdAt,
                updatedAt);
    }

    public DatabaseConnection update(
            String name,
            DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String passwordCiphertext,
            boolean sslEnabled,
            Instant now) {
        return new DatabaseConnection(
                id,
                ownerId,
                workspaceId,
                name,
                type,
                host,
                port,
                databaseName,
                username,
                passwordCiphertext,
                sslEnabled,
                createdAt,
                now);
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

    public DatabaseType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String required(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new DomainException(label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private static String optional(
            String value,
            String label,
            int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainException(label + "不能超过 " + maxLength + " 个字符");
        }
        if (normalized.contains("/")
                || normalized.contains("?")
                || normalized.contains("#")) {
            throw new DomainException(label + "包含无效字符");
        }
        return normalized;
    }

    private static String host(String value) {
        String normalized = required(value, "主机地址", 255);
        if (normalized.contains("://")
                || normalized.contains("/")
                || normalized.contains("?")
                || normalized.contains("#")
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new DomainException("主机地址格式无效");
        }
        return normalized;
    }
}
