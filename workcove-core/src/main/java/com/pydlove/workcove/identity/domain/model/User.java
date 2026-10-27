package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 用户聚合根。身份数据只能在应用服务编排下创建或恢复。 */
public final class User {

    private final UserId id;
    private final Email email;
    private final String displayName;
    private final PasswordHash passwordHash;
    private final Instant createdAt;

    private User(UserId id,
                 Email email,
                 String displayName,
                 PasswordHash passwordHash,
                 Instant createdAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email, "邮箱");
        this.displayName = requireDisplayName(displayName);
        this.passwordHash = Objects.requireNonNull(passwordHash, "密码摘要");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
    }

    public static User register(Email email,
                                String displayName,
                                PasswordHash passwordHash,
                                Instant createdAt) {
        return new User(null, email, displayName, passwordHash, createdAt);
    }

    public static User restore(UserId id,
                               Email email,
                               String displayName,
                               PasswordHash passwordHash,
                               Instant createdAt) {
        return new User(
                Objects.requireNonNull(id, "用户 ID"),
                email,
                displayName,
                passwordHash,
                createdAt);
    }

    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String requireDisplayName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new DomainException("显示名称不能为空");
        }
        if (normalized.length() > 64) {
            throw new DomainException("显示名称不能超过 64 个字符");
        }
        return normalized;
    }
}
