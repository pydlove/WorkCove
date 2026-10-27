package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AuthSession {

    private final Long id;
    private final UserId userId;
    private final SessionTokenHash tokenHash;
    private final Instant createdAt;
    private Instant expiresAt;
    private boolean remembered;

    private AuthSession(Long id,
                        UserId userId,
                        SessionTokenHash tokenHash,
                        Instant expiresAt,
                        boolean remembered,
                        Instant createdAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "用户 ID");
        this.tokenHash = Objects.requireNonNull(tokenHash, "会话摘要");
        this.expiresAt = Objects.requireNonNull(expiresAt, "会话过期时间");
        this.remembered = remembered;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
    }

    public static AuthSession open(UserId userId,
                                   SessionTokenHash tokenHash,
                                   Instant now,
                                   Duration ttl,
                                   boolean remembered) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new DomainException("会话有效期必须大于零");
        }
        return new AuthSession(
                null,
                userId,
                tokenHash,
                now.plus(ttl),
                remembered,
                now);
    }

    public static AuthSession restore(Long id,
                                      UserId userId,
                                      SessionTokenHash tokenHash,
                                      Instant expiresAt,
                                      boolean remembered,
                                      Instant createdAt) {
        return new AuthSession(
                Objects.requireNonNull(id, "会话 ID"),
                userId,
                tokenHash,
                expiresAt,
                remembered,
                createdAt);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public SessionTokenHash getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRemembered() {
        return remembered;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
