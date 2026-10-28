package com.pydlove.workcove.identity.domain.model;

import com.pydlove.workcove.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class EmailVerificationCode {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final Long id;
    private final Email email;
    private final EmailVerificationCodeHash codeHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant consumedAt;
    private int failedAttempts;

    private EmailVerificationCode(Long id,
                                  Email email,
                                  EmailVerificationCodeHash codeHash,
                                  Instant expiresAt,
                                  Instant consumedAt,
                                  int failedAttempts,
                                  Instant createdAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email, "邮箱");
        this.codeHash = Objects.requireNonNull(codeHash, "验证码摘要");
        this.expiresAt = Objects.requireNonNull(expiresAt, "验证码过期时间");
        this.consumedAt = consumedAt;
        this.failedAttempts = failedAttempts;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
    }

    public static EmailVerificationCode issue(Email email,
                                              EmailVerificationCodeHash codeHash,
                                              Instant now,
                                              Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new DomainException("验证码有效期必须大于零");
        }
        return new EmailVerificationCode(
                null,
                email,
                codeHash,
                now.plus(ttl),
                null,
                0,
                now);
    }

    public static EmailVerificationCode restore(Long id,
                                                Email email,
                                                EmailVerificationCodeHash codeHash,
                                                Instant expiresAt,
                                                Instant consumedAt,
                                                int failedAttempts,
                                                Instant createdAt) {
        return new EmailVerificationCode(
                Objects.requireNonNull(id, "验证码 ID"),
                email,
                codeHash,
                expiresAt,
                consumedAt,
                failedAttempts,
                createdAt);
    }

    public boolean verify(EmailVerificationCodeHash candidate, Instant now) {
        if (!isActive(now)) return false;

        if (MessageDigest.isEqual(
                codeHash.value().getBytes(StandardCharsets.US_ASCII),
                candidate.value().getBytes(StandardCharsets.US_ASCII))) {
            consumedAt = now;
            return true;
        }

        failedAttempts += 1;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            consumedAt = now;
        }
        return false;
    }

    public void invalidate(Instant now) {
        if (consumedAt == null) consumedAt = now;
    }

    public boolean isActive(Instant now) {
        return consumedAt == null
                && failedAttempts < MAX_FAILED_ATTEMPTS
                && expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public EmailVerificationCodeHash getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
