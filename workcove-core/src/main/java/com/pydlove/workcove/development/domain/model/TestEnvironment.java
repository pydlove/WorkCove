package com.pydlove.workcove.development.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public final class TestEnvironment {

    private final Long id;
    private final long ownerId;
    private final String workspaceId;
    private final String name;
    private final String url;
    private final String username;
    private final String passwordCiphertext;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TestEnvironment(
            Long id,
            long ownerId,
            String workspaceId,
            String name,
            String url,
            String username,
            String passwordCiphertext,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.workspaceId = required(workspaceId, "工作空间", 64);
        this.name = required(name, "环境名称", 80);
        this.url = normalizedUrl(url);
        this.username = required(username, "用户名", 256);
        this.passwordCiphertext = required(
                passwordCiphertext,
                "密码密文",
                4096);
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间");
    }

    public static TestEnvironment create(
            long ownerId,
            String workspaceId,
            String name,
            String url,
            String username,
            String passwordCiphertext,
            Instant now) {
        return new TestEnvironment(
                null,
                ownerId,
                workspaceId,
                name,
                url,
                username,
                passwordCiphertext,
                now,
                now);
    }

    public static TestEnvironment restore(
            long id,
            long ownerId,
            String workspaceId,
            String name,
            String url,
            String username,
            String passwordCiphertext,
            Instant createdAt,
            Instant updatedAt) {
        return new TestEnvironment(
                id,
                ownerId,
                workspaceId,
                name,
                url,
                username,
                passwordCiphertext,
                createdAt,
                updatedAt);
    }

    public TestEnvironment update(
            String name,
            String url,
            String username,
            String passwordCiphertext,
            Instant now) {
        return new TestEnvironment(
                id,
                ownerId,
                workspaceId,
                name,
                url,
                username,
                passwordCiphertext,
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

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String normalizedUrl(String value) {
        String normalized = required(value, "测试链接", 2048);
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new DomainException("测试链接必须是有效的 HTTP 或 HTTPS 地址");
        }
        return normalized;
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
}
